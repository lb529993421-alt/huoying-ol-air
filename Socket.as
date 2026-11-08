package airgame.bridge {
    import flash.net.Socket;
    import flash.events.Event;
    import flash.events.IOErrorEvent;
    import flash.events.SecurityErrorEvent;
    public class Socket extends flash.net.Socket {
        public static var diagnostics:Array=[];
        private var target:String;
        private var targetPort:int;
        public function Socket(host:String=null,port:int=0){
            super();
            for each(var kind:String in [Event.CONNECT,Event.CLOSE,IOErrorEvent.IO_ERROR,SecurityErrorEvent.SECURITY_ERROR])addEventListener(kind,observed,false,10000);
            if(host!=null)connect(host,port);
        }
        private function record(kind:String,id:int=0):void {
            diagnostics.push({kind:kind,host:target,port:targetPort,errorID:id});
            if(diagnostics.length>40)diagnostics.shift();
        }
        private function observed(e:Event):void {
            record(e.type,e is IOErrorEvent?IOErrorEvent(e).errorID:e is SecurityErrorEvent?SecurityErrorEvent(e).errorID:0);
        }
        override public function connect(host:String,port:int):void {
            target=host;targetPort=port;record("connectRequested");
            try{super.connect(host,port);}catch(error:Error){record(error.name,error.errorID);throw error;}
        }
    }
}
