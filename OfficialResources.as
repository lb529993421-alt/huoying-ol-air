package airgame.bridge {
    import flash.utils.ByteArray;
    import flash.utils.getDefinitionByName;
    import flash.net.registerClassAlias;
    public class OfficialResources {
        public static var base:String;
        public static var normalizedTags:uint=0;
        public static function normalizeIndex(input:ByteArray):ByteArray {
            var Type:Class=RuntimeAdapter.gameDomain.getDefinition("com.tencent.morefun.naruto.file.FileAssetInfo") as Class;
            registerClassAlias("alias.com.tencent.morefun.naruto.file::FileAssetInfo",Type);
            var raw:ByteArray=new ByteArray();raw.writeBytes(input);raw.uncompress("lzma");raw.position=0;
            var length:uint=raw.readUnsignedInt(),start:uint=raw.position;
            if(length>raw.bytesAvailable)throw new Error("resource_index_size");
            var map:Object=raw.readObject();if(raw.position-start!=length)throw new Error("resource_index_encoding");
            var tail:uint=raw.position;normalizedTags=0;
            for each(var item:Object in map){if(item!=null&&typeof(item)=="object"&&"tag" in item&&item.tag){
                var tag:String=String(item.tag).replace(/\/$/,"");
                var resolved:String=resolve(tag+"/").replace(/\/$/,"");
                if(resolved!=tag){item.tag=resolved;normalizedTags++;}
            }}
            var encoded:ByteArray=new ByteArray();encoded.writeObject(map);
            var output:ByteArray=new ByteArray();output.writeUnsignedInt(encoded.length);output.writeBytes(encoded);if(tail<raw.length)output.writeBytes(raw,tail,raw.length-tail);output.compress("lzma");output.position=0;return output;
        }
        public static function select(entry:String):void {
            if(!/^https:\/\/res\.huoying\.qq\.com\/NarutoBeta[0-9.]+Build[0-9]+\/entry\.swf(?:\?|$)/.test(entry))throw new Error("invalid_official_entry");
            base=entry.split("?")[0].replace(/entry\.swf$/ ,"");
        }
        public static function resolve(url:String):String {
            if(base&&url=="app:/resource.cfg")return base+"resource.cfg";
            if(/^(?:app:\/|\/?)(NarutoBeta[0-9.]+Build[0-9]+\/)/.test(url))url="https://res.huoying.qq.com/"+url.replace(/^(app:\/|\/)/,"");
            return url.replace(/^http:\/\/res\.huoying\.qq\.com\//,"https://res.huoying.qq.com/");
        }
        private static function file(url:String):Object {
            if(!/^https:\/\/res\.huoying\.qq\.com\/NarutoBeta[0-9.]+Build[0-9]+\/[A-Za-z0-9_.\/-]+$/.test(url)||url.indexOf("/../")>=0)return null;
            var File:Class=getDefinitionByName("flash.filesystem.File") as Class;
            return File.applicationStorageDirectory.resolvePath("official-resources/"+url.substr("https://res.huoying.qq.com/".length));
        }
        public static function read(url:String):ByteArray {
            try{var target:Object=file(url);if(!target||!target.exists||target.size<=0||target.size>33554432)return null;
                var Stream:Class=getDefinitionByName("flash.filesystem.FileStream") as Class,stream:Object=new Stream(),bytes:ByteArray=new ByteArray();
                stream.open(target,"read");try{stream.readBytes(bytes);}finally{stream.close();}return bytes;
            }catch(error:Error){return null;}
        }
        public static function save(url:String,bytes:ByteArray):void {
            if(!bytes||!bytes.length||bytes.length>33554432)return;
            var temp:Object;
            try{var target:Object=file(url);if(!target)return;target.parent.createDirectory();temp=target.parent.resolvePath(target.name+"."+String(Math.random()).substr(2)+".tmp");
                var Stream:Class=getDefinitionByName("flash.filesystem.FileStream") as Class,stream:Object=new Stream();stream.open(temp,"write");try{stream.writeBytes(bytes);}finally{stream.close();}temp.moveTo(target,true);
            }catch(error:Error){if(temp)try{if(temp.exists)temp.deleteFile();}catch(ignored:Error){}}
        }
    }
}
