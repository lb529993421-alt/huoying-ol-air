package airgame.bridge {
    import flash.display.DisplayObject;
    import flash.events.Event;
    import flash.events.IOErrorEvent;
    import flash.events.SecurityErrorEvent;
    import flash.net.URLLoader;
    import flash.net.URLLoaderDataFormat;
    import flash.net.URLRequest;
    import flash.system.ApplicationDomain;
    import flash.utils.ByteArray;
    import flash.utils.getDefinitionByName;
    import flash.utils.getQualifiedClassName;
    public class Loader extends flash.display.Loader {
        public static var diagnostics:Array=[];
        public static var onDiagnostic:Function;
        private var requestLoader:URLLoader;
        private var source:String="bytes";
        public function Loader() {
            super();
            contentLoaderInfo.addEventListener(Event.INIT,function(e:Event):void{record("init",content?getQualifiedClassName(content):null);});
            contentLoaderInfo.addEventListener(Event.COMPLETE,function(e:Event):void{record("complete",null);});
            contentLoaderInfo.addEventListener(IOErrorEvent.IO_ERROR,error);
            contentLoaderInfo.addEventListener(SecurityErrorEvent.SECURITY_ERROR,error);
            contentLoaderInfo.uncaughtErrorEvents.addEventListener("uncaughtError",function(e:Event):void {
                var value:Object=Object(e).error;record("uncaught",value is Error?Error(value).name+":"+Error(value).errorID+":"+Error(value).message.replace(/\?[^\s]+/g,"?[redacted]").substr(0,250):"event");e.preventDefault();
            });
        }
        private function record(kind:String,detail:String):void {var row:Object={kind:kind,source:source,detail:detail};diagnostics.push(row);if(diagnostics.length>120)diagnostics.shift();if(onDiagnostic!=null)onDiagnostic(row);}
        private function error(event:Event):void {record(event.type,"errorID" in event?String(Object(event).errorID):null);}
        private function permitted(context:flash.system.LoaderContext):flash.system.LoaderContext {
            if(!context) {
                var Context:Class=getDefinitionByName("airgame.bridge.LoaderContext") as Class;
                context=new Context(false);
            }
            if(!context.applicationDomain)context.applicationDomain=new ApplicationDomain(RuntimeAdapter.gameDomain||ApplicationDomain.currentDomain);
            if("allowCodeImport" in context)Object(context).allowCodeImport=true;
            if("allowLoadBytesCodeExecution" in context)Object(context).allowLoadBytesCodeExecution=true;
            return context;
        }
        override public function load(request:URLRequest,context:flash.system.LoaderContext=null):void {
            request.url=OfficialResources.resolve(request.url);source=request.url.split("?")[0];record("request",null);
            requestLoader=new URLLoader();requestLoader.dataFormat=URLLoaderDataFormat.BINARY;
            requestLoader.addEventListener(IOErrorEvent.IO_ERROR,error);
            requestLoader.addEventListener(SecurityErrorEvent.SECURITY_ERROR,error);
            requestLoader.addEventListener(Event.COMPLETE,function(e:Event):void{loadBytes(requestLoader.data as ByteArray,context);});
            requestLoader.load(request);
        }
        override public function loadBytes(bytes:ByteArray,context:flash.system.LoaderContext=null):void {
            record("loadBytes",String(bytes.length));super.loadBytes(bytes,permitted(context));
        }
        override public function close():void {if(requestLoader)try{requestLoader.close();}catch(e:Error){}super.close();}
        override public function unloadAndStop(gc:Boolean=true):void {
            record("beforeUnload", "gc="+gc+" root="+(content?getQualifiedClassName(content):"null")+" hostDomain="+(content!=null&&contentLoaderInfo.applicationDomain===ApplicationDomain.currentDomain));
            super.unloadAndStop(gc);
            record("afterUnload",null);
        }
    }
}
