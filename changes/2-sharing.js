/* ===== SHARING (Capacitor-ready) ===== */
const isNat=()=>{try{return !!(window.Capacitor&&window.Capacitor.isNativePlatform&&window.Capacitor.isNativePlatform())}catch(e){return false}},
cpl=n=>{try{const C=window.Capacitor;return (C.Plugins&&C.Plugins[n])||C.registerPlugin(n)}catch(e){return null}};
const pickMode=()=>new Promise(res=>{const o=document.createElement("div");o.className="shsheet";
 o.innerHTML='<div class="shbox" role="dialog" aria-label="Share options"><b>Share your card</b><button class="btn" data-m="share">Share\u2026</button><button class="btn gh" data-m="save">Save to gallery</button><button class="btn gh" data-m="">Cancel</button></div>';
 const done=m=>{o.remove();res(m)};o.addEventListener("click",e=>{const b=e.target.closest("[data-m]");if(b)done(b.dataset.m||null);else if(e.target===o)done(null)});document.body.appendChild(o)});
const natOut=async(c,mode)=>{
 const data=c.toDataURL("image/png"),b64=data.split(",")[1],name="forge-"+Date.now(),FS=cpl("Filesystem");
 if(!FS)throw new Error("Filesystem plugin missing");
 const w=await FS.writeFile({path:name+".png",data:b64,directory:"CACHE"});          /* 1. canvas -> base64 -> cache file */
 if(mode==="save"){                                                                 /* 2a. Save to gallery */
  const M=cpl("Media");
  if(M){try{const find=async()=>((await M.getAlbums()).albums||[]).find(a=>a.name==="Forge");
   let al=await find();if(!al){await M.createAlbum({name:"Forge"});al=await find()}
   try{await M.savePhoto({path:w.uri,albumIdentifier:al&&al.identifier,fileName:name})}catch(e){await M.savePhoto({path:data,albumIdentifier:al&&al.identifier,fileName:name})}
   toast("Saved to gallery");return}catch(e){console.warn("gallery save failed",e)}}
  await FS.writeFile({path:"Forge/"+name+".png",data:b64,directory:"DOCUMENTS",recursive:true});toast("Saved to Documents/Forge");return}
 const SH=cpl("Share");if(!SH)throw new Error("Share plugin missing");                 /* 2b. Share the file URI */
 await SH.share({title:"Forge",text:"Trained with Forge",files:[w.uri],dialogTitle:"Share your card"})};
const shr=async o=>{try{
 if(isNat()&&!o.mode){o.mode=await pickMode();if(!o.mode)return}
 if(o.b)o.img=await emImg(o.b);
 S.shc=(S.shc||0)+1;save();
 try{await document.fonts.load('800 40px ForgeCard')}catch(e){}
 const c=drawCard(o);
 if(isNat()){await natOut(c,o.mode);return}
 /* browser fallback = unchanged behaviour */
 const b=await new Promise(r=>c.toBlob(r,"image/png")),f=new File([b],"forge.png",{type:"image/png"});
 if(navigator.canShare&&navigator.canShare({files:[f]})){await navigator.share({files:[f],text:"Trained with Forge"});return}
 const a=document.createElement("a");a.href=URL.createObjectURL(b);a.download="forge-"+Date.now()+".png";document.body.appendChild(a);a.click();a.remove();toast("Image saved")
}catch(e){const m=String(e&&(e.message||e)||"");if(!e||(e.name!=="AbortError"&&!/cancel/i.test(m)))toast("Couldn't share")}};