package airgame.bridge {
    import flash.utils.ByteArray;
    import flash.utils.Endian;
    import flash.system.ApplicationDomain;
    public class RuntimeAdapter {
        public static var edits:uint=0;
        public static var emptyImportsRemoved:uint=0;
        public static var redirectNet:Boolean=false;
        public static var redirectTime:Boolean=false;
        public static var gameDomain:ApplicationDomain;
        private static function need(ok:Boolean):void {if(!ok)throw new Error("invalid_public_swf");}
        private static function number(b:ByteArray):uint {
            var value:uint=0;
            for(var i:uint=0;i<5;i++){need(b.bytesAvailable>0);var n:uint=b.readUnsignedByte();value|=(n&127)<<(i*7);if(!(n&128))return value;}
            throw new Error("invalid_abc_integer");
        }
        private static function integer(b:ByteArray,n:uint):void {while(n>=128){b.writeByte((n&127)|128);n>>>=7;}b.writeByte(n);}
        private static function part(out:ByteArray,src:ByteArray,a:uint,z:uint):void {need(a<=z&&z<=src.length);if(z>a)out.writeBytes(src,a,z-a);}
        private static function emptyImport(b:ByteArray,payload:uint,length:uint,code:uint):Boolean {
            var suffix:uint=code==71?5:3,https:String="https://res.huoying.qq.com/empty.swf",http:String="http://res.huoying.qq.com/empty.swf";
            if(length!=https.length+suffix&&length!=http.length+suffix)return false;
            b.position=payload;var url:String=b.readUTFBytes(length-suffix);
            if(url!=https&&url!=http)return false;
            if(b.readUnsignedByte()!=0)return false;
            if(code==71&&(b.readUnsignedByte()!=1||b.readUnsignedByte()!=0))return false;
            return b.readUnsignedShort()==0;
        }
        private static function abc(b:ByteArray,entry:Boolean=false):ByteArray {
            if(entry)b=EntryCompatibility.apply(b);
            b.position=4;var i:uint,j:uint,n:uint,k:uint;
            for(j=0;j<2;j++){n=number(b);for(i=1;i<n;i++)number(b);}
            n=number(b);b.position+=Math.max(0,int(n)-1)*8;need(b.position<=b.length);
            var stringsStart:uint=b.position;var stringCount:uint=Math.max(1,number(b));var stringsData:uint=b.position;
            var strings:Array=[""];
            for(i=1;i<stringCount;i++){n=number(b);need(n<=b.bytesAvailable);strings.push(b.readUTFBytes(n));}
            var namespaceStart:uint=b.position;var namespaceCount:uint=Math.max(1,number(b));var namespaceData:uint=b.position;
            var spaces:Array=[0];
            for(i=1;i<namespaceCount;i++){k=b.readUnsignedByte();spaces.push(number(b));}
            var setsStart:uint=b.position;n=number(b);
            for(i=1;i<n;i++){var c:uint=number(b);for(j=0;j<c;j++)number(b);}
            var namesStart:uint=b.position;var namesCount:uint=number(b);var names:ByteArray=new ByteArray();var changes:uint=0;
            for(i=1;i<namesCount;i++) {
                var start:uint=b.position;k=b.readUnsignedByte();var replace:Boolean=false;
                if(k==7||k==13) {
                    var ns:uint=number(b),name:uint=number(b);need(ns<spaces.length&&name<strings.length&&spaces[ns]<strings.length);
                    var pkg:String=strings[spaces[ns]],simple:String=strings[name];
                    replace=pkg=="flash.external"&&simple=="ExternalInterface"||pkg=="flash.system"&&(simple=="LoaderContext"||simple=="Security")||pkg=="flash.display"&&simple=="Loader"||redirectNet&&pkg=="flash.net"&&(simple=="URLLoader"||simple=="Socket")||redirectTime&&pkg=="flash.utils"&&(simple=="Timer"||simple=="getTimer");
                    if(replace){names.writeByte(k);integer(names,namespaceCount);integer(names,name);changes++;}
                } else if(k==15||k==16||k==27||k==28)number(b);
                else if(k==9||k==14){number(b);number(b);}
                else if(k==29){number(b);c=number(b);for(j=0;j<c;j++)number(b);}
                else need(k==17||k==18);
                if(!replace)part(names,b,start,b.position);
            }
            if(!changes){b.position=0;return b;}
            var tail:uint=b.position,out:ByteArray=new ByteArray();
            part(out,b,0,stringsStart);integer(out,stringCount+1);part(out,b,stringsData,namespaceStart);
            integer(out,14);out.writeUTFBytes("airgame.bridge");integer(out,namespaceCount+1);part(out,b,namespaceData,setsStart);
            out.writeByte(22);integer(out,stringCount);part(out,b,setsStart,namesStart);integer(out,namesCount);out.writeBytes(names);part(out,b,tail,b.length);
            edits+=changes;out.position=0;return out;
        }
        public static function apply(input:ByteArray,entry:Boolean=false):ByteArray {
            if(input.length<8)return input;
            var b:ByteArray=new ByteArray();b.endian=Endian.LITTLE_ENDIAN;input.position=0;b.writeBytes(input);b.position=0;
            var signature:String=b.readUTFBytes(3);
            if(signature!="FWS"&&signature!="CWS"&&signature!="ZWS")return input;
            // The game already unwraps its custom envelope before its normal loadBytes path.
            need(b.length>=8);b.position=4;var size:uint=b.readUnsignedInt();need(size>=12&&size<=134217728);
            var body:ByteArray=new ByteArray();
            if(signature=="CWS"){part(body,b,8,b.length);body.uncompress("zlib");}
            else if(signature=="ZWS") {
                need(b.length>=17);part(body,b,12,17);body.endian=Endian.LITTLE_ENDIAN;body.writeUnsignedInt(size-8);body.writeUnsignedInt(0);part(body,b,17,b.length);body.uncompress("lzma");
            } else part(body,b,8,b.length);
            need(body.length==size-8);b.length=8;b.position=0;b.writeUTFBytes("FWS");b.position=8;b.writeBytes(body);body.clear();
            var offset:uint=8+Math.ceil((5+4*(b[8]>>3))/8)+4,cursor:uint=0,out:ByteArray=new ByteArray();out.endian=Endian.LITTLE_ENDIAN;
            var before:uint=edits,beforeImports:uint=emptyImportsRemoved;
            while(offset<b.length) {
                b.position=offset;need(b.bytesAvailable>=2);var tag:uint=b.readUnsignedShort(),length:uint=tag&63,code:uint=tag>>6;
                if(length==63){need(b.bytesAvailable>=4);length=b.readUnsignedInt();}
                var payload:uint=b.position,end:uint=payload+length;need(end>=payload&&end<=b.length);
                // These exact zero-symbol imports have no content and can interrupt native loadBytes.
                if((code==57||code==71)&&emptyImport(b,payload,length,code)) {
                    part(out,b,cursor,offset);cursor=end;emptyImportsRemoved++;
                } else if(code==72||code==82) {
                    var boundary:uint=payload;
                    if(code==82){boundary+=4;need(boundary<end);while(boundary<end&&b[boundary])boundary++;need(boundary<end);boundary++;}
                    var old:ByteArray=new ByteArray();part(old,b,boundary,end);var prior:uint=edits,changed:ByteArray=abc(old,entry);
                    if(prior!=edits||entry){part(out,b,cursor,offset);out.writeShort((code<<6)|63);out.writeUnsignedInt(boundary-payload+changed.length);part(out,b,payload,boundary);out.writeBytes(changed);cursor=end;}
                }
                offset=end;
            }
            if(!entry&&before==edits&&beforeImports==emptyImportsRemoved){b.position=0;return b;}
            part(out,b,cursor,b.length);out.position=4;out.writeUnsignedInt(out.length);out.position=0;return out;
        }
    }
}
