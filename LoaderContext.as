package airgame.bridge {
    import flash.system.ApplicationDomain;
    import flash.system.SecurityDomain;
    public class LoaderContext extends flash.system.LoaderContext {
        public function LoaderContext(checkPolicyFile:Boolean=false,applicationDomain:ApplicationDomain=null,securityDomain:SecurityDomain=null) {
            super(checkPolicyFile,applicationDomain,securityDomain);
            // AIR requires explicit permission for the game's normal loadBytes module loader.
            if("allowCodeImport" in this)Object(this).allowCodeImport=true;
            if("allowLoadBytesCodeExecution" in this)Object(this).allowLoadBytesCodeExecution=true;
        }
    }
}
