"""Build a standalone native AIR game trial; no login data or PC endpoints in the APK."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import struct
import subprocess
import sys
import time
from getpass import getpass
from build_config import first_abc_offset, local_path, swf_tag

BASE = Path(__file__).resolve().parent
from product_manifest import VERSION,descriptor
from package_patch import stage_apk

def main():
    if sys.argv[1:]:
        raise SystemExit('Usage: python build.py (ARM64, no application activation)')
    sdk = local_path('AIR_SDK')
    jdk = local_path('AIR_JDK')
    android_tools = local_path('ANDROID_SDK_ROOT') / 'build-tools/36.0.0'
    java = jdk / 'bin/java.exe'
    compiler = local_path('AIR_ASC_JAR')
    signing = local_path('AIR_SIGNING_KEYSTORE')
    password = os.environ.get('AIR_SIGNING_PASSWORD') or getpass('APK keystore password: ')
    if not password:
        raise SystemExit('A signing password is required')
    previous=BASE/'release.json'
    if previous.is_file():
        old=json.loads(previous.read_text('utf8'));backup=BASE/('release-'+old['version']+'.json')
        if not backup.exists():shutil.copy2(previous,backup)
    qq_native=json.loads((BASE/'native-qq/compiled.json').read_text('utf8'))
    assert hashlib.sha256(Path(qq_native['ane']).read_bytes()).hexdigest()==qq_native['sha256']
    for name,digest in qq_native['sourceSHA256'].items():assert hashlib.sha256((BASE/'native-qq'/name).read_bytes()).hexdigest()==digest,'Rebuild the changed native login source'
    build=BASE/('build-'+str(time.time_ns()));build.mkdir()
    env = dict(os.environ, JAVA_HOME=str(jdk), AIR_SIGNING_PASSWORD=password)
    for key in ('AIR_ANDROID_SDK_HOME','ANDROID_HOME','ANDROID_SDK_ROOT'):env.pop(key,None)
    commands=[]
    def run(label,command,timeout=150):
        with (build/(label+'.log')).open('wb')as f:
            p=subprocess.Popen(command,cwd=build,env=env,stdout=f,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
            code=p.wait(timeout=timeout)
        commands.append(dict(step=label,pid=p.pid,exitCode=code,heldWaitTerminal=True))
        (build/'commands.json').write_text(json.dumps(commands,indent=2),encoding='utf8')
        assert code==0 and '[Compiler] Error'not in (build/(label+'.log')).read_text('utf8',errors='replace'),str(build/(label+'.log'))
    names=('EntryCompatibility','OfficialResources','RuntimeAdapter','ExternalInterface','LoaderContext','Security','URLLoader','Loader','SpeedClock','Timer','getTimer','Socket','GameBridge','MobileGame')
    for name in names:
        path = BASE / (name + '.as')
        text=path.read_text('utf8')
        if name=='Loader':
            text=text.replace('import flash.net.URLLoader;','').replace('requestLoader:URLLoader','requestLoader:flash.net.URLLoader').replace('new URLLoader()','new flash.net.URLLoader()')
            text=text.replace('super.loadBytes(bytes,permitted(context));','var Adapter:Class=getDefinitionByName("airgame.bridge.RuntimeAdapter") as Class;super.loadBytes(Adapter.apply(bytes),permitted(context));')
            assert 'Adapter.apply(bytes)'in text
        (build/(name+'.as')).write_text(text,encoding='utf8')
    imports = [local_path('AIR_PLAYERGLOBAL_ABC')]
    for name in names:
        cmd=[str(java),'-XX:ActiveProcessorCount=1','-Xmx512m','-jar',str(compiler),'-AS3','-optimize']
        for path in imports:cmd+=['-import',str(path)]
        if name=='MobileGame':cmd+=['-swf','MobileGame,1600,720,31']
        run('compile-'+name,cmd+[str(build/(name+'.as'))],60)
        expected=build/(name+('.swf'if name=='MobileGame'else'.abc'));assert expected.is_file()
        if name!='MobileGame':imports.append(expected)
    movie=(build/'MobileGame.swf').read_bytes();first=first_abc_offset(movie)
    payload=b''.join(swf_tag(82,struct.pack('<I',1)+n.encode()+b'\0'+(build/(n+'.abc')).read_bytes())for n in names[:-1])
    movie=movie[:first]+payload+movie[first:];movie=movie[:3]+bytes([32])+struct.pack('<I',len(movie))+movie[8:]
    (build/'MobileGame.swf').write_bytes(movie)
    shutil.copy2(BASE/'native-qq/qq-return.js',build/'qq-return.js')
    shutil.copy2(Path(qq_native['directory'])/'AndroidX-LICENSE.txt',build/'AndroidX-LICENSE.txt')
    (build/'AndroidX-NOTICE.txt').write_text('AndroidX WebKit 1.14.0 and AndroidX Core 1.13.1\nAndroid Open Source Project\nApache License 2.0. See AndroidX-LICENSE.txt.\nhttps://developer.android.com/jetpack/androidx\n',encoding='utf8')
    (build/'application.xml').write_text(descriptor(require_activation=False), encoding='utf8')
    apk = build / ('huoying-ol-' + VERSION + '-armv8.apk')
    run('package-armv8', [str(java), '-Xmx768m', '-XX:ActiveProcessorCount=2', '-jar', str(sdk/'lib/adt.jar'),
        '-package', '-target', 'apk-captive-runtime', '-arch', 'armv8', '-storetype', 'pkcs12',
        '-keystore', str(signing), '-storepass', password, str(apk), str(build/'application.xml'),
        'MobileGame.swf', 'qq-return.js', 'AndroidX-LICENSE.txt', 'AndroidX-NOTICE.txt', '-extdir', qq_native['extensionDirectory']])
    stage_apk(apk, run, str(java), signing, android_tools)
    package = dict(arch='armv8', apk=str(apk), bytes=apk.stat().st_size, sha256=hashlib.sha256(apk.read_bytes()).hexdigest())
    report = dict(version=VERSION, applicationId='air.local.huoying.airgame', activationRequired=False, packages=[package])
    (BASE/'release.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps(report,ensure_ascii=False),flush=True)

if __name__=='__main__':main()
