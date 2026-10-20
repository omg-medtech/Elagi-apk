(function(){
  if(window.__elagiNative)return;window.__elagiNative=true;
  /* WebView has no Notification API: report it as granted so the sign-up gate passes (reminders are native here) */
  try{
    if(!("Notification" in window)||Notification.permission!=="granted"){
      var N=function(){};N.permission="granted";N.requestPermission=function(cb){if(cb)cb("granted");return Promise.resolve("granted")};
      Object.defineProperty(window,"Notification",{value:N,configurable:true,writable:true});
    }
  }catch(e){}
  /* save PDFs/files generated in the page (blob downloads) to the Downloads folder */
  function saveBlobLink(a){
    var n=a.download||("elagi-"+Date.now()+".pdf");
    fetch(a.href).then(function(r){return r.blob()}).then(function(bl){var fr=new FileReader();fr.onloadend=function(){var d=String(fr.result);AndroidElagi.saveFile(n,bl.type||"",d.substring(d.indexOf(",")+1))};fr.readAsDataURL(bl)});
  }
  try{
    var oc=HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click=function(){if(this.download&&/^blob:/.test(this.href)){saveBlobLink(this);return}return oc.apply(this,arguments)};
    var od=EventTarget.prototype.dispatchEvent;
    EventTarget.prototype.dispatchEvent=function(e){if(this instanceof HTMLAnchorElement&&e&&e.type==="click"&&this.download&&/^blob:/.test(this.href)){saveBlobLink(this);return true}return od.apply(this,arguments)};
  }catch(e){}
  function sync(){
    try{
      if(!window.S||!S.user||typeof window.__alarmText!=="function"){return}
      var now=Date.now(),L=[],u=S.user;
      (u.meds||[]).forEach(function(m){if(m.nextAt>now+2000)L.push({id:"m"+m.id,at:m.nextAt,title:"عِلاجي",body:String(m.name||"").split(/\s+/).slice(0,3).join(" "),text:window.__alarmText("med",m)})});
      (u.tests||[]).forEach(function(t){if(!t.done&&t.at>now+2000)L.push({id:"t"+t.id,at:t.at,title:"عِلاجي",body:t.ar||"",text:window.__alarmText("test",t)})});
      L.sort(function(a,b){return a.at-b.at});
      AndroidElagi.sync(JSON.stringify(L.slice(0,60)));
    }catch(e){}
  }
  window.__nativeSync=sync;
  setInterval(sync,15000);setTimeout(sync,2500);
  document.addEventListener("visibilitychange",sync);
})();
