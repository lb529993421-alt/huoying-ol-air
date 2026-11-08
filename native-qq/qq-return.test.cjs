const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/qq-return.js','utf8');
const home='https://huoying.qq.com',graph='https://graph.qq.com',xui='https://xui.ptlogin2.qq.com',type='huoying-air-qq-return-v1';
function child(origin,initialized=true){
 let receive,clicked=0,launched=0,passwordVisible=false;const posts=[],replaced=[],parent={postMessage:(data,target)=>posts.push({data,target})};
 const frame={contentWindow:{postMessage:(data,target)=>posts.push({data,target})}};
 const window={top:{},parent,pt:{hasInit:true,plogin:{loginState:2,switchpage(){}},doOneKey(){launched++}}};
 vm.runInNewContext(source,{window,location:{origin,replace:u=>replaced.push(u)},URL,
  document:{get readyState(){return initialized?'complete':'loading'},getElementById:id=>id==='switcher_plogin'?{click(){clicked++;if(initialized){passwordVisible=true;window.pt.plogin.loginState=1}}}:id==='p'?{getBoundingClientRect:()=>({height:!initialized||passwordVisible?30:0})}:null,querySelectorAll:()=>[frame]},
  addEventListener:(name,fn)=>receive=fn});
 return {posts,replaced,frame,ready:()=>initialized=true,clicks:()=>clicked,launches:()=>launched,
  send:(data,from=home,src=parent)=>receive({data:{type,...data},origin:from,source:src})};
}
const login=child(xui);
const early=child(xui,false);early.send({action:'password',attempt:'early'});assert.equal(early.posts.length,0,'An unready form must not acknowledge success');
assert.equal(early.clicks(),0,'An unstyled visible input is not proof that the official login script is ready');early.ready();early.send({action:'password',attempt:'early'});early.send({action:'password',attempt:'early'});assert.equal(early.clicks(),1);assert.equal(early.posts.length,1,'Retry the same request after the official form becomes ready');
login.send({action:'password',attempt:'1'},'https://evil.example');assert.equal(login.clicks(),0);
login.send({action:'password',attempt:'1'});login.send({action:'password',attempt:'1'});
assert.equal(login.clicks(),1);assert.equal(login.launches(),0,'Password login must never launch QQ');
login.send({action:'qq',attempt:'2'});login.send({action:'qq',attempt:'2'});assert.equal(login.launches(),1);
login.send({action:'return',url:'https://evil.example/jump'});assert.equal(login.replaced.length,0);
login.send({action:'return',url:'https://ssl.ptlogin2.qq.com/jump?QA_ONLY=1'});assert.equal(login.replaced.length,1);
const relay=child(graph);relay.send({action:'password',attempt:'3'});assert.equal(relay.posts.length,2);
relay.send({action:'mode-ready'},xui,relay.frame.contentWindow);assert.equal(relay.posts.at(-1).target,home);
let tick,receive,cleared=0,now=1000,clicked=0;const posts=[],root={};root.top=root;
const f={src:graph+'/oauth2.0/authorize?state=UNCHANGED&redirect_uri='+encodeURIComponent(home+'/comm-htdocs/login/qc_redirect.html?parent_domain='+home),dataset:{},contentWindow:{postMessage:(data,target)=>posts.push({data,target})}};
const original=f.src;
let documentFrames=[f];
vm.runInNewContext(source,{window:root,location:{origin:home},URL,Date:{now:()=>now},
 document:{querySelectorAll:()=>documentFrames,getElementById:()=>({click(){clicked++}})},
 setInterval:fn=>{tick=fn;return 1},clearInterval:()=>cleared++,addEventListener:(name,fn)=>receive=fn});
root.airQqReturn.mode('password');tick();assert.equal(new URL(f.src).searchParams.get('redirect_uri'),new URL(original).searchParams.get('redirect_uri'));assert(posts.every(p=>p.data.action==='password'));
const before=cleared;receive({origin:xui,source:{},data:{type,action:'mode-ready'}});assert.equal(cleared,before);
receive({origin:xui,source:f.contentWindow,data:{type,action:'mode-ready'}});assert.equal(cleared,before,'Password mode must also reach a later official login frame');
documentFrames=[];now+=1000;tick();assert.equal(clicked,0,'Successful login must not reopen the authorization popup');documentFrames=[f];
root.airQqReturn.mode('qq');tick();const updated=new URL(f.src);
const qqCleared=cleared;receive({origin:xui,source:f.contentWindow,data:{type,action:'mode-ready'}});assert.equal(cleared,qqCleared+1);
assert.equal(updated.searchParams.get('state'),'UNCHANGED');assert.equal(updated.searchParams.get('redirect_uri'),new URL(original).searchParams.get('redirect_uri'));
assert.equal(updated.searchParams.get('display'),'mobile');
posts.length=0;root.airQqReturn.accept('https://ssl.ptlogin2.qq.com/jump?QA_ONLY=1');assert.equal(posts.length,3);
console.log('Password/QQ modes, guarded relay, desktop callback preservation and one-shot dispatch passed.');

// Before the official click handler is ready, retry opening once per second.
root.airQqReturn.mode('password');documentFrames=[];const unopened=clicked;
now+=600;tick();assert.equal(clicked,unopened+1);
now+=300;tick();assert.equal(clicked,unopened+1);
now+=800;tick();assert.equal(clicked,unopened+2);
documentFrames=[f];tick();documentFrames=[];now+=2000;tick();assert.equal(clicked,unopened+2,'A previously opened login must not reopen after success');
console.log('Opening retries wait for an actual iframe and stop after it has appeared.');

// A fresh OAuth cookie must reach the original server-verifying game handler even if Milo's UI cache is stale.
const accountRoot={};accountRoot.top=accountRoot;let originalCalls=0,verifiedCalls=0;
Object.assign(accountRoot,{iUseQQConnect:1,JSON_server:{1039:{sServerStatus:1},1040:{sServerStatus:0}},
 loginNew(){originalCalls++},QcGameLogin(id){assert.equal(id,1039);verifiedCalls++}});
let cookieText='openid=EXAMPLE; access_token=EXAMPLE';
vm.runInNewContext(source,{window:accountRoot,location:{origin:home},URL,Date,Set,
 document:{get cookie(){return cookieText},querySelectorAll:()=>[]},setInterval:()=>1,clearInterval:()=>{},addEventListener:()=>{}});
accountRoot.loginNew(1039);assert.equal(verifiedCalls,1);assert.equal(originalCalls,0);
accountRoot.loginNew(1040);accountRoot.loginNew(9999);assert.equal(verifiedCalls,1);assert.equal(originalCalls,2);
cookieText='openid=EXAMPLE';accountRoot.loginNew(1039);assert.equal(verifiedCalls,1);assert.equal(originalCalls,3);
console.log('QC zone routing keeps the official OAuth/CSRF verification and original unavailable/unlogged handling.');
