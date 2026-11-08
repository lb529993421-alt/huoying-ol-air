package {
    import airgame.bridge.GameBridge;
    import flash.display.Sprite;
    import flash.display.Loader;
    import flash.events.Event;
    import flash.events.KeyboardEvent;
    import flash.geom.Rectangle;
    import flash.net.URLLoader;
    import flash.net.URLLoaderDataFormat;
    import flash.net.URLRequest;
    import flash.system.ApplicationDomain;
    import flash.system.Capabilities;
    import flash.text.TextField;
    import flash.text.TextFormat;
    import flash.utils.ByteArray;
    import flash.utils.Timer;
    import flash.utils.getDefinitionByName;

    public class MobileGame extends Sprite {
        private static const LOG_LIMIT:int=512*1024;
        public static var playersHidden:Boolean=false;
        // The game can remove the host from the stage; keep its timers and native context alive.
        public static var instance:MobileGame;
        public static var appTicks:uint=0;
        public static var appActive:Boolean=true;
        public static var backgroundServiceRunning:Boolean=false;
        private var nativeApp:Object;
        private var browser:Object;
        private var loader:Loader;
        private var status:TextField=new TextField();
        private var poll:Timer=new Timer(1000);
        private var trusted:Boolean=false;
        private var started:Boolean=false;
        private var session:Object;
        private var output:Object;
        private var file:Object;
        private var downloading:URLLoader;
        private var samples:uint=0;
        private var gameReady:Boolean=false;
        private var qq:Object;
        private var qqReady:Boolean=false;
        private var loginScript:URLLoader;
        private var loginMode:String;
        private var sessionWaitStart:Number=0;
        private var sessionWaitHint:Boolean=false;
        private var sessionFailure:String;
        private var bridgeKey:String=String(new Date().time)+"-"+Math.random()+"-"+Math.random();
        private var controlReceived:uint=0;
        private var resizePending:Boolean=false;
        private var desiredSpeed:Number=1;
        private var lastIdentity:String="";
        public function MobileGame(){instance=this;if(stage)init();else addEventListener(Event.ADDED_TO_STAGE,init);}
        private function init(e:Event=null):void {
            removeEventListener(Event.ADDED_TO_STAGE,init);
            stage.scaleMode="noScale";stage.align="TL";stage.frameRate=31;stage.color=0x111827;
            try{stage.displayState="fullScreenInteractive";}catch(error:Error){}
            stage.addEventListener(Event.RESIZE,layout,false,10000);
            stage.addEventListener(Event.ENTER_FRAME,hidePlayers,false,10000);
            stage.addEventListener(KeyboardEvent.KEY_DOWN,function(e:KeyboardEvent):void{if(started&&(e.keyCode==0x01000016||e.keyCode==4||e.keyCode==27)){e.preventDefault();e.stopImmediatePropagation();if(qqReady)qq.call("returnToSelection");}},true,1000);
            status.defaultTextFormat=new TextFormat("_sans",18,0xffffff);status.width=1000;status.height=42;status.y=3;status.x=12;status.mouseEnabled=false;addChild(status);
            nativeApp=Object(getDefinitionByName("flash.desktop.NativeApplication")).nativeApplication;
            nativeApp.addEventListener(Event.ACTIVATE,function(e:Event):void{appActive=true;if(started)resizePending=true;record("生命周期 activate ticks="+appTicks);});
            nativeApp.addEventListener(Event.DEACTIVATE,function(e:Event):void{appActive=false;if(started)resizePending=true;record("生命周期 deactivate ticks="+appTicks);});
            Object(getDefinitionByName("airgame.bridge.RuntimeAdapter")).redirectNet=true;
            Object(getDefinitionByName("airgame.bridge.RuntimeAdapter")).redirectTime=true;
            Object(getDefinitionByName("airgame.bridge.Loader")).onDiagnostic=function(row:Object):void{record("模块执行 "+row.kind+" "+row.source+" "+row.detail);};
            Object(getDefinitionByName("airgame.bridge.URLLoader")).onDiagnostic=function(row:Object):void{report("文件 "+row.kind+" "+row.url+" attempt="+row.attempt+" status="+row.status);};
            report("火影OL · 正在初始化");
            loaderInfo.uncaughtErrorEvents.addEventListener("uncaughtError",function(ev:Event):void {
                var err:Object=Object(ev).error;var detail:String=err is Error?Error(err).message:"";
                if(/access_token|openid|password|cookie|skey/i.test(detail))detail="";
                report("运行错误 "+(err is Error?Error(err).name+" #"+Error(err).errorID:"event")+" "+detail.replace(/https?:\/\/[^\s]+/g,"[URL]").substr(0,100));
                if(err is Error && Error(err).getStackTrace())report(Error(err).getStackTrace().replace(/https?:\/\/[^\s]+/g,"[URL]").substr(0,180));ev.preventDefault();
            });
            poll.addEventListener("timer",inspect);poll.start();openLogin();layout();
        }
        private function layout(e:Event=null):void {
            if(started&&(!appActive||resizePending)){
                if(e)e.stopImmediatePropagation();
                record("后台尺寸延后 "+stage.stageWidth+"x"+stage.stageHeight+" active="+appActive);return;
            }
            status.width=Math.max(100,stage.stageWidth-16);
            if(browser&&!started)browser.viewPort=new Rectangle(0,0,stage.stageWidth,Math.max(1,stage.stageHeight));
        }
        private function report(message:String):void {
            if(!/^(文件 |模块 |资源读取 |内置资源索引 |索引解码 |入口容器 |入口初始化 )/.test(message))status.text=message;
            record(message);
        }
        private function record(message:String):void {
            // Only bounded event labels are persisted, never page URLs, cookies or login values.
            try{
                if(output&&(output.position>=LOG_LIMIT||!file.exists)){output.close();output=null;}
                if(!output){
                    var File:Class=getDefinitionByName("flash.filesystem.File") as Class;
                    var Stream:Class=getDefinitionByName("flash.filesystem.FileStream") as Class;
                    file=File.applicationStorageDirectory.resolvePath("air-diagnostics-"+String(new Date().time)+"-"+String(100000+int(Math.random()*900000))+".jsonl");
                    var next:Object=new Stream();next.open(file,"append");output=next;
                }
                output.writeUTFBytes(JSON.stringify({time:new Date().time,event:message.substr(0,180)})+"\n");
            }catch(err:Error){}
            if(qq)try{qq.call("diagnostic",message.substr(0,180));}catch(nativeLogError:Error){}
        }
        private function openLogin(mode:String=null):void {
            record("登录入口 started="+started+" native="+qqReady+" stage="+stage.stageWidth+"x"+stage.stageHeight);
            if(started)return;
            loginMode=mode;
            sessionWaitStart=0;sessionWaitHint=false;sessionFailure=null;
            if(!browser){var Type:Class=getDefinitionByName("flash.media.StageWebView") as Class;browser=new Type(true);browser.addEventListener("locationChanging",locationChanging);browser.addEventListener("complete",pageComplete);browser.addEventListener("error",function(e:Event):void{loginIssue("登录网页加载失败，请点顶部重载选区");});}
            trusted=false;browser.stage=stage;layout();
            if(qqReady){loadLogin();return;}
            if(qq){loadLogin();return;}
            try {
                qq=Object(getDefinitionByName("QqAirLib")).create();
                if(!qq)throw new Error("QQ_native_unavailable");
                if(qq.call("licenseActive")!==true){report("请先激活火影OL");return;}
                qq.addEventListener("status",function(e:Event):void{
                    var code:String=Object(e).code;
                    if(code=="host_resumed"){appActive=true;if(started){nativeApp.executeInBackground=true;resizePending=false;stage.dispatchEvent(new Event(Event.RESIZE));}layout();record("宿主焦点和尺寸已恢复");return;}
                    if(code=="speed_changed"){desiredSpeed=Number(Object(e).level);applySpeed();return;}
                    if(code=="selection_requested"){if(started)qq.call("returnToSelection");else openLogin();return;}
                    if(code=="login_requested"){if(!started)openLogin(String(Object(e).level));else report("请先返回选区再切换登录方式");return;}
                    if(code=="game_control"){control(String(Object(e).level));return;}
                    if(code=="game_session"){acceptSession(String(Object(e).level),true);return;}
                    if(code=="background_state"){record("后台联网 "+String(Object(e).level)+" ticks="+appTicks);return;}
                    if(code=="qq_ready"){qqReady=true;loadLogin();}
                    else if(code=="qq_launched")report("已打开手机 QQ，请选择账号并授权");
                    else if(code=="qq_returned")report("QQ 授权已返回，正在完成网页登录");
                    else if(code=="background_started")backgroundServiceRunning=true;
                    else if(code=="background_stopped")backgroundServiceRunning=false;
                    else if(code=="qq_link_setup")report("请允许登录链接返回火影OL，完成后返回应用");
                    else if(code=="qq_native_error"){qqReady=false;loadLogin();loginIssue("QQ 登录组件暂不可用，可在官网页面登录");}
                    else if(code=="qq_launch_failed"||code=="qq_request_rejected"||code=="qq_return_not_accepted")report("QQ 登录尚未完成，请重新发起或使用网页登录");
                    record(code);
                });
                loginScript=new URLLoader();loginScript.addEventListener(Event.COMPLETE,function(e:Event):void{qq.call("install",String(loginScript.data));});
                loginScript.addEventListener("ioError",function(e:Event):void{loadLogin();loginIssue("QQ 登录组件读取失败，可在官网页面登录");});loginScript.load(new URLRequest("app:/qq-return.js"));
            }catch(error:Error){qq=null;report("QQ 组件初始化 "+error.name+" #"+error.errorID+" "+error.message.substr(0,80));loadLogin();}
        }
        private function loginIssue(message:String):void {
            report(message);
            if(qq)try{qq.call("loginNotice",message);}catch(error:Error){}
        }
        private function loadLogin():void {if(loginMode&&qqReady)qq.call("mode",loginMode);else browser.loadURL("https://huoying.qq.com/server/website/");report("请登录 QQ 并选择区服，随后进入游戏");}
        private function pageComplete(e:Event):void {record("登录网页 complete");try{trusted=/^https:\/\/game\.huoying\.qq\.com\//.test(browser.location);}catch(err:Error){trusted=false;}inspect();}
        private function hidePlayers(e:Event=null):void {
            if(!started||playersHidden)return;
            playersHidden=GameBridge.hideOtherPlayers(stage);
            if(playersHidden){stage.removeEventListener(Event.ENTER_FRAME,hidePlayers);record("玩家屏蔽已确认");}
        }
        private function inspect(e:Event=null):void {
            if(e&&e.type=="timer")appTicks++;
            if(started){hidePlayers();if(!appActive)return;
                if(resizePending&&stage.stageWidth>0&&stage.stageHeight>0){resizePending=false;stage.dispatchEvent(new Event(Event.RESIZE));record("前台尺寸恢复 "+stage.stageWidth+"x"+stage.stageHeight);}
                if(parent===stage)stage.setChildIndex(this,stage.numChildren-1);
                if(!gameReady)try{var app:Object=Object(Object(getDefinitionByName("airgame.bridge.RuntimeAdapter")).gameDomain.getDefinition("base.ApplicationData")).singleton;if(app.selfInfo&&app.selfInfo.level>0){gameReady=true;applySpeed();status.visible=false;report("主城角色数据已就绪");}}catch(error:Error){}
                if(gameReady&&qqReady)try{var identity:Object=Object(Object(getDefinitionByName("airgame.bridge.RuntimeAdapter")).gameDomain.getDefinition("base.ApplicationData")).singleton;var label:String=String(identity.selfuin)+"|"+String(identity.selfSrv)+"|"+String(identity.selfInfo.name);if(label!=lastIdentity){lastIdentity=label;qq.call("identity",String(identity.selfuin),String(identity.selfSrv),String(identity.selfInfo.name));}}catch(identityError:Error){}
                if(++samples%10==0&&samples<=120){report("资源读取 "+downloading.bytesLoaded+"/"+downloading.bytesTotal);var diag:Object=Object(getDefinitionByName("airgame.bridge.Loader")).diagnostics;for each(var row:Object in diag.slice(-3))report("模块 "+row.kind+" "+row.source+" "+row.detail);
                    diag=Object(getDefinitionByName("airgame.bridge.URLLoader")).diagnostics;for each(row in diag.slice(-6))report("文件 "+row.kind+" "+row.url+" "+row.bytes+" "+(row.error||""));}return;}
            if(!browser)return;
            try {
                if(!/^https:\/\/game\.huoying\.qq\.com\//.test(browser.location)){sessionWaitStart=0;sessionWaitHint=false;sessionFailure=null;return;}
                trusted=true;
                if(!sessionWaitStart)sessionWaitStart=new Date().time;
                if(!sessionWaitHint&&new Date().time-sessionWaitStart>=15000){sessionWaitHint=true;loginIssue("选区后未收到登录信息，可点重载选区后重新登录");}
                browser.loadURL("javascript:(function(){"
                    +"if(location.origin!=='https://game.huoying.qq.com'||!window.web||typeof web.getUin!=='function')return;"
                    +"var entry=document.getElementById('entry');if(!entry)return;"
                    +"var src=entry.src||entry.getAttribute('src')||entry.getAttribute('data'),vars=entry.getAttribute('flashvars');"
                    +"Array.prototype.forEach.call(entry.querySelectorAll('param'),function(param){"
                    +"var name=String(param.name).toLowerCase();if(name==='movie'&&!src)src=param.value;if(name==='flashvars'&&!vars)vars=param.value;});"
                    +"if(!src)return;var url=new URL(src,document.baseURI||location.href);"
                    +"var cookies=Object.create(null),parameters=Object.create(null),page=Object.create(null);"
                    +"document.cookie.split(';').forEach(function(part){var i=part.indexOf('=');if(i<0)return;"
                    +"var key=part.slice(0,i).trim();if(key)cookies[key]=typeof getCookie==='function'?getCookie(key):part.slice(i+1);});"
                    +"if(!cookies.access_token||!cookies.openid)return;"
                    +"url.searchParams.forEach(function(value,key){parameters[key]=value;});"
                    +"if(vars)new URLSearchParams(vars).forEach(function(value,key){parameters[key]=value;});"
                    +"new URL(location.href).searchParams.forEach(function(value,key){page[key]=value;});"
                    +"var data={account:String(web.getUin()).trim(),entryURL:url.href,parameters:parameters,pageParameters:page,cookies:cookies,userAgent:navigator.userAgent};"
                    +"location.href='huoyingair-session:'+encodeURIComponent(JSON.stringify(data));})()");
            }catch(err:Error){report("登录信息尚未就绪");}
        }
        private function locationChanging(e:Event):void {
            var url:String=Object(e).location;
            if(url.indexOf("wtloginmqq:")==0){e.preventDefault();if(qq)qq.call("open",url);else report("手机 QQ 登录组件尚未就绪");return;}
            if(url.indexOf("huoyingair-control:")==0) {
                e.preventDefault();control(url);return;
            }
            if(url.indexOf("huoyingair-session:")!=0)return;
            e.preventDefault();acceptSession(url);
        }
        private function acceptSession(url:String,nativeOrigin:Boolean=false):void {
            if(started||(nativeOrigin?!qqReady:!trusted))return;
            var phase:String="会话格式";
            var bridge:Object;
            try {
                var d:Object=JSON.parse(decodeURIComponent(url.substr("huoyingair-session:".length)));
                d.entryURL=String(d.entryURL).replace(/^http:\/\/res\.huoying\.qq\.com\//,"https://res.huoying.qq.com/");
                phase="QQ 账号格式";
                if(!/^\d{5,20}$/.test(d.account))throw new Error("account");
                phase="官方入口地址";
                Object(getDefinitionByName("airgame.bridge.OfficialResources")).select(d.entryURL);
                phase="区服编号";
                if(!d.parameters||!/^\d{1,6}$/.test(String(d.parameters.zone_id)))throw new Error("zone");
                phase="区服服务器地址";
                if(String(d.parameters.ip).toLowerCase().replace(/\.$/,"")!="zone.huoying.qq.com")throw new Error("host");
                phase="QQ 登录凭据";
                if(!d.cookies||!d.cookies.access_token||!d.cookies.openid)throw new Error("cookies");
                phase="启动游戏容器";
                record("官方入口版本 "+String(d.entryURL).split("/")[3]);
                // Desktop OAuth routing must not change the game's Android compatibility path.
                if(qqReady){var gameAgent:Object=qq.call("gameUserAgent");if(gameAgent)d.userAgent=String(gameAgent);}
                nativeApp.executeInBackground=true;record("后台执行 enabled");
                browser.loadURL("javascript:window.airControlKey="+JSON.stringify(bridgeKey)+";void(0)");
                session=d;session.returns={};started=true;browser.stage=null;
                bridge=getDefinitionByName("airgame.bridge.ExternalInterface");bridge.settings=d;bridge.notify=external;
                var Type:Class=getDefinitionByName("airgame.bridge.Loader") as Class;loader=new Type() as Loader;
                loader.contentLoaderInfo.addEventListener("ioError",function(ev:Event):void{report("游戏入口下载失败，请退出后重试");});
                loader.contentLoaderInfo.addEventListener(Event.INIT,function(ev:Event):void{report("入口初始化 "+loader.contentLoaderInfo.loaderURL.split("?")[0]);});
                loader.contentLoaderInfo.addEventListener(Event.COMPLETE,function(ev:Event):void{stage.scaleMode="noScale";stage.dispatchEvent(new Event(Event.RESIZE));report("火影OL · 区服 "+d.parameters.zone_id);report("入口容器 "+loader.content.loaderInfo.loaderURL.split("?")[0]);});
                addChildAt(loader,0);report("正在下载游戏资源，请稍候…");
                downloading=new URLLoader();downloading.dataFormat=URLLoaderDataFormat.BINARY;
                downloading.addEventListener("ioError",function(ev:Event):void{report("官方资源下载失败，请从菜单重启当前账号后重试");});
                downloading.addEventListener(Event.COMPLETE,function(ev:Event):void {
                    // Keep game library unloading outside the host native extension domain.
                    var domain:ApplicationDomain=new ApplicationDomain(ApplicationDomain.currentDomain);Object(getDefinitionByName("airgame.bridge.RuntimeAdapter")).gameDomain=domain;
                    var Context:Class=getDefinitionByName("airgame.bridge.LoaderContext") as Class;var context:Object=new Context(false,domain);context.parameters=d.parameters;
                    try{var entryBytes:ByteArray=Object(getDefinitionByName("airgame.bridge.RuntimeAdapter")).apply(downloading.data as ByteArray,true);loader.loadBytes(entryBytes,context);}catch(adaptError:Error){report("入口兼容适配失败，请分享诊断日志："+adaptError.message.substr(0,60));}
                });
                downloading.load(new URLRequest(String(d.entryURL).split("?")[0]));
                if(qqReady)qq.call("gameStarted");
            }catch(err:Error){
                if(started){
                    if(downloading)try{downloading.close();}catch(closeError:Error){}
                    if(loader&&loader.parent===this)removeChild(loader);
                    if(bridge)bridge.settings=null;
                    session=null;started=false;browser.stage=stage;layout();
                }
                sessionWaitHint=true;
                if(sessionFailure!=phase){sessionFailure=phase;loginIssue("登录失败 · "+phase+"。可重载选区或保存诊断日志");record("登录衔接失败 "+phase+" "+err.name+" #"+err.errorID);}
            }
        }
        private function control(url:String):void {
            if(++controlReceived<=200)record("控制收到 "+controlReceived+" started="+started+" trusted="+trusted+" native="+qqReady);
            if(!started||(!qqReady&&!trusted))return;
            try{var request:Object=JSON.parse(decodeURIComponent(url.substr("huoyingair-control:".length)));if(request.key!==bridgeKey)return;
                var result:Object;try{result={seq:request.seq,ok:true,value:Object(getDefinitionByName("airgame.bridge.GameBridge")).execute(stage,request.command)};}catch(error:Error){result={seq:request.seq,ok:false,error:error.name+":"+error.errorID};}
                if(qqReady)qq.call("controlReply",JSON.stringify(result));else browser.loadURL("javascript:window.airControlResult="+JSON.stringify(result)+";void(0)");
            }catch(error:Error){}
        }
        private function applySpeed():void {
            try{Object(getDefinitionByName("airgame.bridge.SpeedClock")).setSpeed(desiredSpeed);if(gameReady)stage.frameRate=30*desiredSpeed;}catch(error:Error){desiredSpeed=1;report("倍速设置失败，已保留原速");}
        }
        private function external(message:Object):void {
            // Run normal game-to-page notifications only in its retained official login page.
            if(!browser||(!qqReady&&!trusted))return;
            var name:String=message.method;
            if(!/^[A-Za-z_$][A-Za-z0-9_$.]{0,120}$/.test(name)||name=="open"||name=="window.open"||name=="location.reload")return;
            try {var script:String="(function(){var n="+JSON.stringify(name)+",a="+JSON.stringify(message.arguments)+",o=window,p=n.split('.');if(p[0]==='window')p.shift();var k=p.pop();for(var i=0;i<p.length;i++){o=o&&o[p[i]];if(!o)return;}if(o&&typeof o[k]==='function')o[k].apply(o,a);})()";if(qqReady)qq.call("gameScript",script);else browser.loadURL("javascript:"+script);}catch(err:Error){}
        }
    }
}
