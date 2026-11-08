"""Package the Java-only QQ handoff as an AIR native extension using installed tools."""
from pathlib import Path
import hashlib
import io
import json
import os
import struct
import subprocess
import sys
import time
import zipfile

BASE=Path(__file__).resolve().parent
sys.path.insert(0, str(BASE.parent))
from build_config import local_path, swf_tag

def main():
    out=BASE/('build-'+str(time.time_ns()));out.mkdir();classes=out/'classes';classes.mkdir()
    sdk = local_path('AIR_SDK')
    jdk = local_path('AIR_JDK')
    java = str(jdk/'bin/java.exe')
    javac = str(jdk/'bin/javac.exe')
    android_sdk = local_path('ANDROID_SDK_ROOT')
    cache = local_path('ANDROIDX_CACHE')
    jars=[]
    for group,version in [('androidx.webkit/webkit','1.14.0'),('androidx.core/core','1.13.1')]:
        source=next((cache/group/version).glob('*/*.aar'))
        with zipfile.ZipFile(source)as z:
            assert z.testzip()is None
            if group=='androidx.webkit/webkit':
                (out/'AndroidX-LICENSE.txt').write_bytes(z.read('META-INF/androidx/webkit/webkit/LICENSE.txt'))
            for name in z.namelist():
                if name=='classes.jar' or name.startswith('libs/')and name.endswith('.jar'):
                    p=out/(source.stem+'-'+name.replace('/','-'));p.write_bytes(z.read(name));jars.append(p)
    annotation=next((cache/'androidx.annotation/annotation-jvm/1.9.1').glob('*/*.jar'))
    fre=sdk/'lib/android/FlashRuntimeExtensions.jar';android=android_sdk/'platforms/android-36/android.jar'
    commands=[]
    def run(name,command):
        with(out/(name+'.log')).open('wb')as f:
            p=subprocess.Popen(command,cwd=out,stdout=f,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
            code=p.wait(timeout=120)
        commands.append(dict(step=name,pid=p.pid,exitCode=code,heldWaitTerminal=True));(out/'commands.json').write_text(json.dumps(commands,indent=2),encoding='utf8')
        assert code==0,(out/(name+'.log')).read_text('utf8',errors='replace')[-3000:]
    stub=out/'stubsrc/air/local/huoying/airgame/AppEntry.java';stub.parent.mkdir(parents=True);stub.write_text('package air.local.huoying.airgame; public class AppEntry extends android.app.Activity {}',encoding='utf8')
    stubs=out/'stubs';stubs.mkdir();run('entry-stub',[javac,'--release','8','-cp',str(android),'-d',str(stubs),str(stub)])
    stubjar=out/'entry-stub.jar'
    with zipfile.ZipFile(stubjar,'w')as z:
        for path in stubs.rglob('*.class'):z.write(path,path.relative_to(stubs).as_posix())
    cp=';'.join(map(str,[android,fre,annotation,stubjar]+jars))
    run('javac',[javac,'--release','8','-encoding','UTF-8','-cp',cp,'-d',str(classes),*[str(BASE/n)for n in ('AuthUrls.java','AuthUrlsTest.java','LicenseToken.java','OfflineLicense.java','Accounts.java','DisplayLayout.java','DisplayLayoutTest.java','AirProduct.java','AccountBootstrap.java','Diagnostics.java','AirAccountActivity.java','AccountEntries.java','QqExtension.java')]])
    if '--skip-tests'not in sys.argv:
        run('contracts',[java,'-cp',str(classes),'local.huoying.air.auth.AuthUrlsTest'])
        run('display-contracts',[java,'-cp',str(classes),'local.huoying.air.auth.DisplayLayoutTest'])
    library=out/'qqlogin.jar'
    with zipfile.ZipFile(library,'w',zipfile.ZIP_DEFLATED)as z:
        added=set()
        for path in classes.rglob('*.class'):
            if path.name.startswith(('AuthUrlsTest','DisplayLayoutTest')):continue
            name=path.relative_to(classes).as_posix();z.write(path,name);added.add(name)
        for jar in jars:
            with zipfile.ZipFile(jar)as source:
                for name in source.namelist():
                    if name.endswith('.class')and name not in added:z.writestr(name,source.read(name));added.add(name)
    # Pre-dex with the installed matching Android platform; AIR's old D8 then only merges dex.
    dex=out/'dex';dex.mkdir()
    run('dex',[java,'-XX:ActiveProcessorCount=2','-Xmx768m','-cp',str(android_sdk/'build-tools/36.0.0/lib/d8.jar'),'com.android.tools.r8.D8','--release','--min-api','21','--lib',str(android),'--lib',str(fre),'--classpath',str(stubjar),'--output',str(dex),str(library)])
    assert (dex/'classes.dex').is_file() and (dex/'classes.dex').read_bytes().startswith(b'dex\n035\0')
    with zipfile.ZipFile(library,'w',zipfile.ZIP_DEFLATED)as z:
        for path in dex.glob('*.dex'):z.write(path,path.name)
    # Minimal AS3 library; the application uses ExtensionContext directly.
    (out/'QqAirLib.as').write_text('package { import flash.utils.getDefinitionByName; public class QqAirLib { public function QqAirLib() {} public static function create():Object { return Object(getDefinitionByName("flash.external.ExtensionContext")).createExtensionContext("local.huoying.qqlogin", ""); } } }',encoding='utf8')
    compiler = local_path('AIR_ASC_JAR')
    imports = local_path('AIR_PLAYERGLOBAL_ABC')
    run('asc',[java,'-XX:ActiveProcessorCount=1','-Xmx384m','-jar',str(compiler),'-AS3','-import',str(imports),str(out/'QqAirLib.as')]);assert(out/'QqAirLib.abc').is_file()
    tags=swf_tag(69,struct.pack('<I',8))+swf_tag(82,struct.pack('<I',1)+b'QqAirLib\0'+(out/'QqAirLib.abc').read_bytes())+swf_tag(1,b'')+swf_tag(0,b'')
    movie=b'FWS'+bytes([32])+struct.pack('<I',14+len(tags))+b'\x08\x00\x00\x18\x01\x00'+tags
    assert len(movie)==int.from_bytes(movie[4:8],'little')
    (out/'library.swf').write_bytes(movie)
    catalog='''<swc xmlns="http://www.adobe.com/flash/swccatalog/9"><versions><swc version="1.2"/></versions><features><feature-script-deps/><feature-files/></features><libraries><library path="library.swf"><script name="QqAirLib" mod="0"><def id="QqAirLib"/><dep id="Object" type="i"/></script></library></libraries><files/></swc>'''
    with zipfile.ZipFile(out/'qqlogin.swc','w',zipfile.ZIP_DEFLATED)as z:z.writestr('catalog.xml',catalog);z.writestr('library.swf',movie)
    platforms=['Android-ARM','Android-ARM64','Android-x86','Android-x64']
    xml='''<extension xmlns="http://ns.adobe.com/air/extension/33.1"><id>local.huoying.qqlogin</id><versionNumber>0.0.1</versionNumber><platforms>'''+''.join('<platform name="'+p+'"><applicationDeployment><nativeLibrary>qqlogin.jar</nativeLibrary><initializer>local.huoying.air.auth.QqExtension</initializer></applicationDeployment></platform>'for p in platforms)+'''<platform name="default"><applicationDeployment/></platform></platforms></extension>'''
    (out/'extension.xml').write_text(xml,encoding='utf8');extdir=out/'extensions';extdir.mkdir();ane=extdir/'qqlogin.ane'
    command=[java,'-XX:ActiveProcessorCount=2','-Xmx512m','-jar',str(sdk/'lib/adt.jar'),'-package','-target','ane',str(ane),str(out/'extension.xml'),'-swc',str(out/'qqlogin.swc')]
    for platform in platforms:command+=['-platform',platform,'-C',str(out),'qqlogin.jar','library.swf']
    command+=['-platform','default','-C',str(out),'library.swf'];run('ane',command)
    with zipfile.ZipFile(ane)as z:assert z.testzip()is None
    record=dict(directory=str(out),extensionDirectory=str(extdir),ane=str(ane),sha256=hashlib.sha256(ane.read_bytes()).hexdigest(),sourceSHA256={n:hashlib.sha256((BASE/n).read_bytes()).hexdigest()for n in('AuthUrls.java','LicenseToken.java','OfflineLicense.java','Accounts.java','DisplayLayout.java','AirProduct.java','AccountBootstrap.java','Diagnostics.java','AirAccountActivity.java','AccountEntries.java','QqExtension.java','qq-return.js')})
    (BASE/'compiled.json').write_text(json.dumps(record,indent=2),encoding='utf8');print('QQ native extension packaged:',ane,flush=True)

if __name__=='__main__':main()
