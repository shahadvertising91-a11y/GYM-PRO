<script>
/* ===== 2) IN-APP STICKY BAR  +  3) NATIVE NOTIFICATION BAR HOOKS ===== */
(function(){
  const nat=()=>{try{return !!(window.Capacitor&&window.Capacitor.isNativePlatform&&window.Capacitor.isNativePlatform())}catch(e){return false}};
  const plug=()=>{try{const C=window.Capacitor;return (C.Plugins&&C.Plugins.ForgeBar)||C.registerPlugin("ForgeBar")}catch(e){return null}};
  const H_=()=>(typeof H==="function"?H():(S.h||170));
  const kcalPerStep=()=>H_()/100*(S.sex==="f"?.413:.415)/1000*.57*S.w;      /* same formula as the Steps card */

  /* ---------- NativeBar: JS bridge to the Android foreground service (no-op outside Android) ---------- */
  const listeners=[];let lastPush=0,saveT=0;
  const NativeBar={
    active:false,steps:null,day:null,
    isNative:nat,
    async start(opts){
      if(!nat())return false;const P=plug();if(!P)return false;
      try{await P.start({goal:(opts&&opts.goal)||S.sg||8000,kps:kcalPerStep()});}catch(e){console.warn("NativeBar.start",e);return false}
      NativeBar.active=true;S.nbOn=1;S.nbOff=0;save();
      try{if(window.__pd){removeEventListener("devicemotion",window.__pd.sample);window.__pd.PD.on=1}}catch(e){}   /* native counter replaces the JS one */
      return true},
    async update(d){
      if(!nat()||!NativeBar.active)return false;const P=plug();if(!P)return false;
      try{await P.update({steps:d&&d.steps,kcal:d&&d.kcal,goal:S.sg||8000,kps:kcalPerStep()});return true}catch(e){return false}},
    async stop(){
      if(!nat())return false;const P=plug();
      try{P&&await P.stop()}catch(e){}
      NativeBar.active=false;S.nbOn=0;save();return true},
    onSteps(cb){listeners.push(cb);return()=>{const i=listeners.indexOf(cb);if(i>=0)listeners.splice(i,1)}}
  };
  window.NativeBar=NativeBar;

  /* single entry point for the stats shown in the bar (browser: existing counter; Android: native counter) */
  window.getTodayStats=function(){
    const d=TK();
    const steps=(NativeBar.active&&NativeBar.steps!=null&&NativeBar.day===d)?NativeBar.steps:stp();
    let kcal=steps*kcalPerStep();
    try{S.hist.forEach(h=>{if(h.d===d)kcal+=kb(h.min)})}catch(e){}
    return{steps:Math.max(0,Math.round(steps)),kcal:Math.max(0,Math.round(kcal))}};

  function onNativeSteps(e){
    const d=TK(),prev=stp(),n=Math.max(0,+e.steps||0);
    NativeBar.steps=n;NativeBar.day=d;
    S.steps[d]=n;                                              /* keep the rest of the app (Steps card, badges) in sync */
    if(n>prev){const h=new Date().getHours();(S.stH[d]=S.stH[d]||Array(24).fill(0))[h]+=n-prev}
    clearTimeout(saveT);saveT=setTimeout(()=>{try{save()}catch(x){}},3000);
    refreshBar();
    const t=Date.now();if(t-lastPush>15000){lastPush=t;NativeBar.update(getTodayStats())}   /* refresh kcal text in the notification */
    listeners.slice().forEach(f=>{try{f(getTodayStats())}catch(x){}});
    window.dispatchEvent(new CustomEvent("nativebar:steps",{detail:getTodayStats()}))}

  if(nat()){const P=plug();
    try{P&&P.addListener("steps",onNativeSteps)}catch(e){}
    try{P&&P.addListener("state",s=>{NativeBar.active=!!s.running;if(!s.running){S.nbOn=0;S.nbOff=1;if(window.__pd)window.__pd.PD.on=0;save();render()}})}catch(e){}
    /* Android: the Steps card's Start/Pause button controls the native foreground service */
    const _stx=ACT.stx;
    ACT.stx=function(v){
      if(NativeBar.active){NativeBar.stop().then(()=>{S.stOn=0;S.nbOff=1;save();if(window.__pd)window.__pd.PD.on=0;toast("Step counting paused");render()});return}
      NativeBar.start({goal:S.sg||8000}).then(ok=>{if(ok){S.stOn=1;save();toast("Counting steps");render()}else if(_stx)_stx.call(ACT,v)})};
    /* phone notification bar: ask for permission and start at every launch, unless the user paused it */
    if(!S.nbOff)setTimeout(()=>NativeBar.start({goal:S.sg||8000}),1500);
  }

  /* ---------- sticky bar on Home ---------- */
  const FOOT='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 3c2 0 3 2 3 5s-1 4-3 4-3-1-3-4 1-5 3-5zM7 15h2v2a1 1 0 0 1-2 0zM16 9c2 0 3 2 3 5s-1 4-3 4-3-1-3-4 1-5 3-5zM15 20h2v1a1 1 0 0 1-2 0z"/></svg>';
  function barHtml(){const s=getTodayStats(),g=S.sg||8000;
    return '<div class="fgbar" id="fgbar" data-a="tools" role="button" aria-label="Today\'s steps and calories"><span>'+FOOT+'<b id="fgst">'+s.steps.toLocaleString()+'</b><em>steps</em></span><span>'+ico("flame")+'<b id="fgkc">'+s.kcal.toLocaleString()+'</b><em>kcal</em></span><span class="fgs" id="fggl">'+Math.round(Math.min(1,s.steps/g)*100)+'% of goal</span><i id="fgpg" style="width:'+Math.min(100,s.steps/g*100)+'%"></i></div>'}
  function refreshBar(){const el=document.getElementById("fgbar");if(!el)return;const s=getTodayStats(),g=S.sg||8000,set=(i,t)=>{const e=document.getElementById(i);if(e&&e.textContent!==t)e.textContent=t};
    set("fgst",s.steps.toLocaleString());set("fgkc",s.kcal.toLocaleString());set("fggl",Math.round(Math.min(1,s.steps/g)*100)+"% of goal");
    const p=document.getElementById("fgpg");if(p)p.style.width=Math.min(100,s.steps/g*100)+"%"}
  const IN_APP_BAR=false;           /* the phone notification bar replaces it; set true to show the slim bar on Home again */
  if(IN_APP_BAR){const _home=homeV;homeV=function(){return barHtml()+_home.apply(this,arguments)}}
  setInterval(refreshBar,1000);
  if(typeof render==="function")render();
})();
</script>
