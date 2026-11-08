package airgame.bridge {
    import flash.utils.getDefinitionByName;
    public class Security {
        private static var implementation:Object=getDefinitionByName("flash.system.Security");
        public static const APPLICATION:String="application";
        public static const REMOTE:String="remote";
        public static const LOCAL_TRUSTED:String="localTrusted";
        public static const LOCAL_WITH_FILE:String="localWithFile";
        public static const LOCAL_WITH_NETWORK:String="localWithNetwork";
        public static function get sandboxType():String {return implementation.sandboxType;}
        public static function allowDomain(...rest):void {
            // AIR application content already has network access and rejects this browser-only call.
            if(sandboxType!=APPLICATION)implementation.allowDomain.apply(null,rest);
        }
        public static function allowInsecureDomain(...rest):void {if(sandboxType!=APPLICATION)implementation.allowInsecureDomain.apply(null,rest);}
        public static function loadPolicyFile(url:String):void {if(sandboxType!=APPLICATION)implementation.loadPolicyFile(url);}
        public static function get exactSettings():Boolean {return implementation.exactSettings;}
        public static function set exactSettings(value:Boolean):void {if(sandboxType!=APPLICATION)implementation.exactSettings=value;}
        public static function showSettings(panel:String="default"):void {implementation.showSettings(panel);}
    }
}
