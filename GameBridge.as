package airgame.bridge {
    import flash.display.DisplayObject;
    import flash.display.DisplayObjectContainer;
    import flash.display.InteractiveObject;
    import flash.display.MovieClip;
    import flash.display.SimpleButton;
    import flash.display.Sprite;
    import flash.display.Stage;
    import flash.system.ApplicationDomain;
    import flash.events.MouseEvent;
    import flash.text.TextField;
    import flash.text.StaticText;
    import flash.utils.Dictionary;
    import flash.utils.getDefinitionByName;
    import flash.utils.getQualifiedClassName;
    public class GameBridge {
        private static var objects:Object={};
        private static var ids:Dictionary=new Dictionary(true);
        private static var nextID:uint=1;
        public static var playerHideAttempts:uint=0;
        public static var playerHideFound:uint=0;
        public static function hideOtherPlayers(stage:Stage):Boolean {
            playerHideAttempts++;
            var main:DisplayObjectContainer=stage.getChildByName("mainUI") as DisplayObjectContainer;
            if(!main)return false;
            var pending:Array=[main],budget:uint=5000,cursor:uint=0;
            while(cursor<pending.length&&budget--){
                var node:DisplayObject=pending[cursor++] as DisplayObject;
                if(!node.visible||node.alpha==0)continue;
                if(node.name=="otherPlayerVisibleBtn"&&node is MovieClip){
                    playerHideFound++;
                    var clip:MovieClip=MovieClip(node);if(clip.currentFrame==2)return true;if(clip.currentFrame!=1)return false;
                    // The delegated HUD handler reads target.parent.name and uses that parent as the toggle.
                    if(clip.numChildren==0)return false;node=clip.getChildAt(0);
                    node.dispatchEvent(new MouseEvent(MouseEvent.MOUSE_DOWN,true,false,0,0,null,false,false,false,true));
                    node.dispatchEvent(new MouseEvent(MouseEvent.MOUSE_UP,true));node.dispatchEvent(new MouseEvent(MouseEvent.CLICK,true));
                    node.dispatchEvent(new MouseEvent(MouseEvent.MOUSE_OUT,true));node.dispatchEvent(new MouseEvent(MouseEvent.ROLL_OUT,false));
                    return clip.currentFrame==2;
                }
                var parent:DisplayObjectContainer=node as DisplayObjectContainer;
                if(parent)for(var i:int=0;i<parent.numChildren;i++)pending.push(parent.getChildAt(i));
            }
            return false;
        }
        private static function walk(node:DisplayObject,path:String,rows:Array,depth:uint=0):void {
            if(depth>30||rows.length>=1500||!node.visible||node.alpha==0||/chat|talk/i.test(path))return;
            var id:String=ids[node];if(!id){id="n"+nextID++;ids[node]=id;}objects[id]=node;
            var text:String=node is TextField?TextField(node).text:node is StaticText?StaticText(node).text:null;
            if(text&&text.replace(/\s/g,"").length||/close|btn|button|restPK|autoFight|defaultOk|getOut|confirm|sure|^ok$/i.test(node.name)||node.name=="arena"||node is SimpleButton||node is Sprite&&Sprite(node).buttonMode||/naruto\.component\.controls::Button/.test(getQualifiedClassName(node))) {
                var row:Object={id:id,name:node.name,path:path,type:getQualifiedClassName(node)};
                if(text)row.text=text.substr(0,180);
                if(node is MovieClip)row.frame=MovieClip(node).currentFrame;
                if(node is InteractiveObject)row.mouseEnabled=InteractiveObject(node).mouseEnabled;
                if("enabled"in node)row.enabled=Object(node).enabled;
                var rect:Object=node.getBounds(node.stage);row.bounds=[rect.x,rect.y,rect.width,rect.height];rows.push(row);
            }
            var parent:DisplayObjectContainer=node as DisplayObjectContainer;
            if(parent)for(var i:uint=0;i<parent.numChildren;i++){var child:DisplayObject=parent.getChildAt(i);walk(child,path+"."+child.name,rows,depth+1);}
        }
        public static function execute(stage:Stage,command:Object):Object {
            if(command.op=="ping")return {alive:true};
            if(command.op=="state"||command.op=="status") {
                var identity:Object=null,battle:Object=null;
                try{var a:Object=Object(RuntimeAdapter.gameDomain.getDefinition("base.ApplicationData")).singleton,info:Object=a.selfInfo;identity={account:String(a.selfuin),zone:String(a.selfSrv),name:info?info.name:null,level:info?info.level:null,dianquan:info?info.dianquan:null,yuanbao:info?info.yuanbao:null,isFightLock:a.isFightLock};var b:Object=a.getModel("battleStart");if(b)battle={isFighting:b.isFighting,type:b.type,replayBytes:b.replayData?b.replayData.length:0};}catch(error:Error){}
                var rows:Array=[];if(command.op=="state"){objects={};walk(stage,"stage",rows);}
                var host:Object=getDefinitionByName("MobileGame"),app:Object=Object(getDefinitionByName("flash.desktop.NativeApplication")).nativeApplication;
                return {network:Socket.diagnostics,identity:identity,battle:battle,nodes:rows,externalCalls:Object(getDefinitionByName("airgame.bridge.ExternalInterface")).calls,runtime:{playerHideAttempts:playerHideAttempts,playerHideFound:playerHideFound,playersHidden:host.playersHidden,hostAttached:Boolean(host.instance&&host.instance.parent===stage),gameDomainIsolated:Boolean(RuntimeAdapter.gameDomain&&RuntimeAdapter.gameDomain!==ApplicationDomain.currentDomain),speed:SpeedClock.multiplier,virtualTime:SpeedClock.now(),ticks:host.appTicks,active:host.appActive,executeInBackground:app.executeInBackground,backgroundServiceRunning:host.backgroundServiceRunning},bundledAssetReads:Object(getDefinitionByName("airgame.bridge.URLLoader")).bundledAssetReads,bundledBootstrapReads:Object(getDefinitionByName("airgame.bridge.URLLoader")).bundledBootstrapReads,files:Object(getDefinitionByName("airgame.bridge.URLLoader")).diagnostics,loaders:Object(getDefinitionByName("airgame.bridge.Loader")).diagnostics,stage:{width:stage.stageWidth,height:stage.stageHeight,frameRate:stage.frameRate,scaleMode:stage.scaleMode,displayState:stage.displayState}};
            }
            if(command.op=="click") {
                var node:DisplayObject=objects[command.id];
                if(!node||node.stage!==stage||!node.visible||node.name!==command.name||getQualifiedClassName(node)!==command.type)throw new Error("stale_or_wrong_control");
                if("enabled"in node&&!Object(node).enabled)throw new Error("disabled_control");
                node.dispatchEvent(new MouseEvent(MouseEvent.MOUSE_OVER,true));node.dispatchEvent(new MouseEvent(MouseEvent.MOUSE_DOWN,true,false,0,0,null,false,false,false,true));node.dispatchEvent(new MouseEvent(MouseEvent.MOUSE_UP,true));node.dispatchEvent(new MouseEvent(MouseEvent.CLICK,true));node.dispatchEvent(new MouseEvent(MouseEvent.MOUSE_OUT,true));
                return {dispatched:true,name:node.name,type:getQualifiedClassName(node)};
            }
            throw new Error("unsupported_bridge_operation");
        }
    }
}
