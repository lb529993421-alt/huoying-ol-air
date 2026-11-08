"""Correct AIR's duplicate AppEntry declaration, preserving all other compiled resources."""
import hashlib
import json
import struct
import zipfile


def patch_manifest(raw):
    assert raw[:4]==b'\x03\x00\x08\x00'and struct.unpack_from('<I',raw,4)[0]==len(raw)
    chunks=[];position=8
    while position<len(raw):
        kind,header,size=struct.unpack_from('<HHI',raw,position)
        assert size>=header>=8 and position+size<=len(raw)
        chunks.append((kind,raw[position:position+size]));position+=size
    pool=next(c for k,c in chunks if k==1);header=struct.unpack_from('<H',pool,2)[0]
    count,styles,flags,start=struct.unpack_from('<IIII',pool,8)
    def string(index):
        assert index<count
        offset=start+struct.unpack_from('<I',pool,header+index*4)[0]
        if flags&256:
            for _ in range(2):
                n=pool[offset];offset+=1
                if n&128:n=((n&127)<<8)|pool[offset];offset+=1
            return pool[offset:offset+n].decode('utf8')
        n=struct.unpack_from('<H',pool,offset)[0];offset+=2
        if n&32768:n=((n&32767)<<16)|struct.unpack_from('<H',pool,offset)[0];offset+=2
        return pool[offset:offset+n*2].decode('utf-16le')
    ids=next(c for k,c in chunks if k==0x180);resource_ids=struct.unpack_from('<'+'I'*((len(ids)-8)//4),ids,8)
    def attributes(chunk):
        at,size,n=struct.unpack_from('<HHH',chunk,24);assert size==20
        return [chunk[16+at+i*20:16+at+(i+1)*20]for i in range(n)]
    def value(attribute):
        index=struct.unpack_from('<I',attribute,8)[0]
        return string(index)if index!=0xffffffff else struct.unpack_from('<I',attribute,16)[0]
    entries=[];stack=[];ends={}
    for index,(kind,chunk)in enumerate(chunks):
        if kind==0x102:
            tag=string(struct.unpack_from('<I',chunk,20)[0]);stack.append(index)
            attrs={string(struct.unpack_from('<I',a,4)[0]):a for a in attributes(chunk)}
            if tag=='activity'and'name'in attrs and value(attrs['name'])in('.AppEntry','air.local.huoying.airgame.AppEntry'):entries.append((index,attrs))
        elif kind==0x103:
            assert stack;ends[stack.pop()]=index
    assert not stack
    if len(entries)==1:
        assert value(entries[0][1]['enabled'])==0
        return raw,dict(modified=False,idempotent=True)
    assert len(entries)==2,'Unexpected AIR entry layout'
    (main,first),(duplicate,second)=entries
    assert'enabled'not in first and set(second)=={'name','enabled'}and value(second['enabled'])==0 and ends[duplicate]==duplicate+1
    old=chunks[main][1];at,size,n=struct.unpack_from('<HHH',old,24);assert struct.unpack_from('<HHH',old,30)==(0,0,0)
    attrs=attributes(old)+[second['enabled']]
    def order(a):
        index=struct.unpack_from('<I',a,4)[0];assert index<len(resource_ids)and resource_ids[index]>0;return resource_ids[index]
    attrs.sort(key=order)
    changed=bytearray(old[:16+at]+b''.join(attrs)+old[16+at+n*size:]);struct.pack_into('<I',changed,4,len(changed));struct.pack_into('<H',changed,28,n+1)
    keep=[]
    for index,(kind,chunk)in enumerate(chunks):
        if duplicate<=index<=ends[duplicate]:continue
        keep.append(bytes(changed)if index==main else chunk)
    result=bytearray(raw[:8]+b''.join(keep));struct.pack_into('<I',result,4,len(result));result=bytes(result)
    assert patch_manifest(result)[0]==result
    return result,dict(modified=True,duplicateRemoved=True,generatedEntryDisabled=True,otherChunksUnchanged=True,idempotent=True,originalSHA256=hashlib.sha256(raw).hexdigest(),patchedSHA256=hashlib.sha256(result).hexdigest())


def stage_apk(apk,run,java,signing,android_tools):
    unsigned=apk.with_name(apk.stem+'-unsigned.apk');aligned=apk.with_name(apk.stem+'-aligned.apk')
    with zipfile.ZipFile(apk)as src,zipfile.ZipFile(unsigned,'w')as dst:
        manifest,proof=patch_manifest(src.read('AndroidManifest.xml'))
        for item in src.infolist():
            if item.filename.startswith('META-INF/')and item.filename.rsplit('/',1)[-1].upper().endswith(('.SF','.RSA','.DSA','.EC','.MF')):continue
            dst.writestr(item,manifest if item.filename=='AndroidManifest.xml'else src.read(item.filename))
    run('align-entry-'+apk.stem,[str(android_tools/'zipalign.exe'),'-f','-P','16','4',str(unsigned),str(aligned)])
    run('sign-entry-'+apk.stem,[java,'-XX:ActiveProcessorCount=1','-Xmx256m','-jar',str(android_tools/'lib/apksigner.jar'),'sign','--ks',str(signing),'--ks-pass','env:AIR_SIGNING_PASSWORD','--out',str(apk),str(aligned)])
    with zipfile.ZipFile(apk)as final,zipfile.ZipFile(unsigned)as before:
        assert final.testzip()is None
        assert all(final.read(n)==before.read(n)for n in before.namelist())
    (apk.parent/(apk.stem+'-entry-patch.json')).write_text(json.dumps(proof,indent=2),encoding='utf8')
