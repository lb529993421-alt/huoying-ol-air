package airgame.bridge {
    import flash.net.URLRequest;
    import flash.events.Event;
    import flash.events.IOErrorEvent;
    import flash.events.HTTPStatusEvent;
    import flash.utils.Dictionary;
    import flash.utils.Timer;
    public class URLLoader extends flash.net.URLLoader {
        public static var diagnostics:Array=[];
        public static var bundledBootstrapReads:uint=0;
        public static var bundledAssetReads:uint=0;
        public static var onDiagnostic:Function;
        private static var active:Dictionary=new Dictionary();
        private static const TIMEOUT_MS:uint=30000;
        private static const DNS_TIMEOUT_MS:uint=5000;
        private static const RETRY_DELAY_MS:uint=500;
        private var source:String;
        private var request:URLRequest;
        private var attempt:uint=0;
        private var status:int=0;
        private var eligible:Boolean=false;
        private var dnsLookup:Boolean=false;
        private var terminal:Boolean=false;
        private var cancelled:Boolean=false;
        private var watchdog:Timer;
        private var retryTimer:Timer;
        private var wrappers:Dictionary=new Dictionary(true);
        public function URLLoader(request:URLRequest=null) {
            super();
            super.addEventListener(Event.COMPLETE,complete,false,int.MAX_VALUE);
            super.addEventListener("ioError",failed,false,int.MAX_VALUE);
            super.addEventListener("securityError",failed,false,int.MAX_VALUE);
            super.addEventListener("progress",function(e:Event):void{if((eligible||dnsLookup)&&watchdog&&!terminal&&!cancelled){watchdog.reset();watchdog.start();}});
            if(request)load(request);
        }
        private function record(kind:String):void {var row:Object={kind:kind,url:source,bytes:bytesLoaded,attempt:attempt,status:status};diagnostics.push(row);if(diagnostics.length>80)diagnostics.shift();if(eligible&&onDiagnostic&&/^(cached|retry|timeout|failed|index_normalized|index_invalid)$/.test(kind))onDiagnostic(row);}
        private function stopTimers():void {if(watchdog)watchdog.stop();if(retryTimer)retryTimer.stop();}
        private function httpStatus(e:HTTPStatusEvent):void {status=e.status;}
        private function begin():void {
            if(cancelled)return;
            attempt++;status=0;record("request");
            var cached:flash.utils.ByteArray=eligible&&!/\/(resource\.cfg|entry\.swf)$/.test(source)?OfficialResources.read(source):null;
            if(cached){active[this]=true;flash.utils.setTimeout(function():void{if(cancelled||terminal)return;data=dataFormat=="binary"?cached:cached.toString();record("cached");dispatchEvent(new Event(Event.COMPLETE));},0);return;}
            var next:URLRequest=request;
            if(eligible||dnsLookup){active[this]=true;Object(next).idleTimeout=dnsLookup?DNS_TIMEOUT_MS:TIMEOUT_MS;watchdog.reset();watchdog.start();}
            try{super.load(next);}catch(err:Error){if(!eligible)throw err;if(!recover())dispatchEvent(new IOErrorEvent("ioError",false,false,"静态资源无法打开",2032));}
        }
        private function recover():Boolean {
            stopTimers();
            if(eligible&&attempt<3&&!cancelled){
                record("retry");
                try{super.close();}catch(ignored:Error){}
                Object(request).useCache=false;
                retryTimer.delay=RETRY_DELAY_MS*attempt;retryTimer.reset();retryTimer.start();return true;
            }
            terminal=true;delete active[this];record("failed");return false;
        }
        private function complete(e:Event):void {
            if(cancelled){e.stopImmediatePropagation();return;}
            if(eligible&&status>=400){e.stopImmediatePropagation();if(!recover())dispatchEvent(new IOErrorEvent("ioError",false,false,"静态资源 HTTP 错误",2032));return;}
            if(eligible&&dataFormat=="binary"&&data is flash.utils.ByteArray)OfficialResources.save(source,data as flash.utils.ByteArray);
            if(eligible&&/\/resource\.cfg$/.test(source)&&dataFormat=="binary"){
                try{data=OfficialResources.normalizeIndex(data as flash.utils.ByteArray);record("index_normalized");}
                catch(error:Error){e.stopImmediatePropagation();terminal=true;stopTimers();delete active[this];record("index_invalid");dispatchEvent(new IOErrorEvent("ioError",false,false,"资源索引地址适配失败",2032));return;}
            }
            terminal=true;stopTimers();delete active[this];record("complete");
        }
        private function failed(e:Event):void {
            if(cancelled){e.stopImmediatePropagation();return;}
            if(terminal)return;
            record(e.type);if(recover())e.stopImmediatePropagation();
        }
        override public function addEventListener(type:String,listener:Function,useCapture:Boolean=false,priority:int=0,useWeakReference:Boolean=false):void {
            var key:String=type+":"+useCapture;var group:Object=wrappers[listener];if(!group){group={};wrappers[listener]=group;}
            if(!group[key])group[key]=function(e:Event):void{try{listener(e);}catch(err:Error){var text:String=err.name+":"+err.errorID+":"+err.message+" "+err.getStackTrace();if(/access_token|openid|skey|password|cookie/i.test(text))text=err.name+":"+err.errorID;diagnostics.push({kind:"listenerError",url:source,bytes:bytesLoaded,error:text.substr(0,600)});throw err;}};
            super.addEventListener(type,group[key],useCapture,priority,useWeakReference);
        }
        override public function removeEventListener(type:String,listener:Function,useCapture:Boolean=false):void {
            var group:Object=wrappers[listener],key:String=type+":"+useCapture;super.removeEventListener(type,group&&group[key]?group[key]:listener,useCapture);
        }
        override public function load(request:URLRequest):void {
            stopTimers();delete active[this];
            var resolved:String=OfficialResources.resolve(request.url);
            if(resolved!=request.url){var replacement:URLRequest=new URLRequest(resolved);replacement.method=request.method;replacement.data=request.data;replacement.requestHeaders=request.requestHeaders;request=replacement;}
            this.request=request;attempt=0;terminal=false;cancelled=false;
            source=request.url.split("?")[0];
            eligible=/^https:\/\/res\.huoying\.qq\.com\/NarutoBeta[0-9.]+Build\d+\//.test(request.url)&&request.method=="GET"&&request.data==null&&request.requestHeaders.length==0;
            // This legacy DNS service has its own game fallback, but never starts it while a request hangs.
            dnsLookup=/^http:\/\/182\.254\.116\.117\/d\?dn=[a-z0-9.-]+$/i.test(request.url)&&request.method=="GET"&&request.data==null&&request.requestHeaders.length==0;
            // AIR exposes HTTP error bodies when this listener is present; leave API semantics intact.
            super.removeEventListener("httpResponseStatus",httpStatus);
            if(eligible)super.addEventListener("httpResponseStatus",httpStatus);
            if((eligible||dnsLookup)&&!watchdog){
                watchdog=new Timer(TIMEOUT_MS,1);retryTimer=new Timer(RETRY_DELAY_MS,1);
                retryTimer.addEventListener("timer",function(e:Event):void{begin();});
                watchdog.addEventListener("timer",function(e:Event):void{record("timeout");try{superClose();}catch(ignored:Error){}if(!recover())dispatchEvent(new IOErrorEvent("ioError",false,false,"静态资源请求超时",2032));});
            }
            if(watchdog)watchdog.delay=dnsLookup?DNS_TIMEOUT_MS:TIMEOUT_MS;
            if(eligible&&/\/(resource\.cfg|entry\.swf)$/.test(source))Object(request).useCache=false;
            begin();
        }
        private function superClose():void {super.close();}
        override public function close():void {cancelled=true;stopTimers();delete active[this];super.close();}
    }
}
