package airgame.bridge {
    public class ExternalInterface {
        public static var settings:Object;
        public static var notify:Function;
        public static var callbacks:Object={};
        public static var calls:Object={};
        public static var marshallExceptions:Boolean=false;
        public static function get available():Boolean {return settings!=null;}
        public static function get objectID():String {return "entry";}
        public static function addCallback(name:String,callback:Function):void {callbacks[name]=callback;}
        public static function call(name:String,...rest):* {
            calls[name]=(calls[name]||0)+1;
            if(name=="getUin")return settings.account;
            if(name=="getCookie")return settings.cookies[String(rest[0])]||"";
            if(name=="getUrlParam")return settings.pageParameters[String(rest[0])]||null;
            if(name=="window.navigator.userAgent.toLowerCase")return String(settings.userAgent).toLowerCase();
            if(settings.returns && name in settings.returns)return settings.returns[name];
            if(notify!=null)notify({event:"external",method:name,arguments:rest});
            return null;
        }
    }
}
