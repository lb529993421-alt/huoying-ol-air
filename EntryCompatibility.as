package airgame.bridge {
    import flash.utils.ByteArray;
    public class EntryCompatibility {
        private static var b:ByteArray,strings:Array,names:Array,kinds:Array,targets:Object,fields:Object;
        private static function need(ok:Boolean):void {if(!ok)throw new Error("entry_layout_changed");}
        private static function u():uint {var n:uint=0;for(var i:uint=0;i<5;i++){need(b.bytesAvailable>0);var c:uint=b.readUnsignedByte();n|=(c&127)<<(7*i);if(!(c&128))return n;}throw new Error("entry_integer_invalid");}
        private static function put(out:ByteArray,n:uint):void {while(n>=128){out.writeByte((n&127)|128);n>>>=7;}out.writeByte(n);}
        private static function copy(out:ByteArray,a:uint,z:uint):void {need(z>=a&&z<=b.length);if(z>a)out.writeBytes(b,a,z-a);}
        private static function trait(owner:String):void {
            var name:uint=u(),kind:uint=b.readUnsignedByte(),type:uint=kind&15,method:uint;
            if(type==0||type==6){u();u();if(u())b.readUnsignedByte();if(owner=="KeyFileLoader")fields[names[name]]=name;}
            else if(type==1||type==2||type==3){u();method=u();if(owner=="entry"&&names[name]=="addBuildVersion"||owner=="KeyFileLoader"&&(names[name]=="loadNextRSL"||names[name]=="getConfig"))targets[owner+"."+names[name]]=method;}
            else if(type==4||type==5){u();u();}else need(false);
            if(kind&64){var n:uint=u();while(n--)u();}
        }
        private static function traits(owner:String):void {var n:uint=u();while(n--)trait(owner);}
        public static function apply(input:ByteArray):ByteArray {
            b=input;b.position=4;strings=[""];names=[""];kinds=[0];targets={};fields={};
            var i:uint,j:uint,n:uint,k:uint,a:uint,count:uint,classes:uint;
            for(j=0;j<2;j++){n=u();for(i=1;i<n;i++)u();}n=u();b.position+=Math.max(0,int(n)-1)*8;
            n=u();for(i=1;i<n;i++){k=u();need(k<=b.bytesAvailable);strings.push(b.readUTFBytes(k));}
            n=u();for(i=1;i<n;i++){b.readUnsignedByte();u();}n=u();for(i=1;i<n;i++){k=u();while(k--)u();}
            n=u();for(i=1;i<n;i++){k=b.readUnsignedByte();kinds.push(k);var name:String="";
                if(k==7||k==13){u();name=strings[u()];}else if(k==15||k==16){name=strings[u()];}else if(k==9||k==14){name=strings[u()];u();}else if(k==27||k==28)u();else if(k==29){u();count=u();while(count--)u();}else need(k==17||k==18);names.push(name);
            }
            n=u();for(i=0;i<n;i++){count=u();u();for(j=0;j<count;j++)u();u();var flags:uint=b.readUnsignedByte();if(flags&8){k=u();while(k--){u();b.readUnsignedByte();}}if(flags&128)for(j=0;j<count;j++)u();}
            n=u();for(i=0;i<n;i++){u();count=u();for(j=0;j<count*2;j++)u();}
            classes=u();for(i=0;i<classes;i++){var owner:String=names[u()];u();flags=b.readUnsignedByte();if(flags&8)u();count=u();while(count--)u();u();traits(owner);}
            for(i=0;i<classes;i++){u();traits("");}n=u();for(i=0;i<n;i++){u();traits("");}
            n=u();var records:Array=[];for(i=0;i<n;i++){a=b.position;var method:uint=u(),stackAt:uint=b.position;u();var stackEnd:uint=b.position;u();u();u();var lengthAt:uint=b.position,length:uint=u(),codeAt:uint=b.position;need(length<=b.bytesAvailable);b.position+=length;var end:uint=b.position,exceptions:uint=u();for(j=0;j<exceptions*5;j++)u();traits("");records.push({a:a,z:b.position,method:method,stackAt:stackAt,stackEnd:stackEnd,lengthAt:lengthAt,codeAt:codeAt,end:end,exceptions:exceptions});}
            need(b.position==b.length);var menu:Object=null,rsl:Object=null,getter:Object=null;
            for each(var row:Object in records){if(row.method===targets["entry.addBuildVersion"])menu=row;if(row.method===targets["KeyFileLoader.loadNextRSL"])rsl=row;if(row.method===targets["KeyFileLoader.getConfig"])getter=row;}
            if(!menu&&!rsl){input.position=0;return input;}
            need(menu!=null&&rsl!=null&&getter!=null&&fields._cfg>0&&fields._byteAssets>0&&menu.exceptions==0&&rsl.exceptions==0);
            b.position=menu.codeAt;var boundary:uint=0;
            while(b.position<Math.min(menu.end,menu.codeAt+24)){k=b.readUnsignedByte();if(k==0x61||k==0x68){var property:uint=u();need(names[property]=="gameVersion");boundary=b.position;break;}else if(k==0x60||k==0x66||k==0x2c)u();else need(k==0xd0||k==0xd1||k==0x30);}
            need(boundary>menu.codeAt);var menuCode:ByteArray=new ByteArray();copy(menuCode,menu.codeAt,boundary);menuCode.writeByte(0x47);
            var dynamic:uint=0;for(a=getter.codeAt;a<getter.end;a++){if(b[a]!=0x66)continue;b.position=a+1;k=u();if(kinds[k]==27||kinds[k]==28){dynamic=k;break;}}
            var plugin:int=strings.indexOf("pluginCfg");need(dynamic>0&&plugin>0&&b[rsl.codeAt]==0xd0&&b[rsl.codeAt+1]==0x30);
            var rslCode:ByteArray=new ByteArray();copy(rslCode,rsl.codeAt,rsl.codeAt+2);rslCode.writeByte(0xd0);rslCode.writeByte(0x66);put(rslCode,fields._byteAssets);rslCode.writeByte(0xd0);rslCode.writeByte(0x66);put(rslCode,fields._cfg);rslCode.writeByte(0x2c);put(rslCode,plugin);rslCode.writeByte(0x66);put(rslCode,dynamic);rslCode.writeByte(0x66);put(rslCode,dynamic);rslCode.writeByte(0x11);rslCode.writeByte(1);rslCode.writeByte(0);rslCode.writeByte(0);rslCode.writeByte(0x47);copy(rslCode,rsl.codeAt+2,rsl.end);
            var out:ByteArray=new ByteArray(),cursor:uint=0;
            for each(row in records){var replacement:ByteArray=row===menu?menuCode:row===rsl?rslCode:null;if(!replacement)continue;copy(out,cursor,row.stackAt);b.position=row.stackAt;put(out,Math.max(3,u()));copy(out,row.stackEnd,row.lengthAt);put(out,replacement.length);out.writeBytes(replacement);cursor=row.end;}
            copy(out,cursor,b.length);out.position=0;return out;
        }
    }
}
