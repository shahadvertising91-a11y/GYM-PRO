#!/usr/bin/env python3
"""Apply the Forge native/Firebase patch to a Forge single-file HTML.
usage: python3 scripts/build_html.py forge-gym-v11_1.html www/index.html"""
import sys,re,os
here=os.path.dirname(os.path.abspath(__file__));ch=os.path.join(here,"..","changes")
rd=lambda n:open(os.path.join(ch,n),encoding="utf-8").read()
src,dst=sys.argv[1],sys.argv[2]
s=open(src,encoding="utf-8").read()
def rep(old,new,count=1,label=""):
    global s
    n=s.count(old)
    assert n==count,f"{label or old[:50]!r}: expected {count} match(es), found {n}"
    s=s.replace(old,new)

# 0. title
s=s.replace("<title>Forge: train like a pro</title>","<title>Forge Gym Workout for Men</title>",1)
# 1. bundled font + css (head)
rep("</head>",rd("1-head-font.html")+"</head>",1,"</head>")
# 2. sharing: swap the old shr() for the Capacitor-ready one
a=s.index("const shr=async o=>{");endm='toast("Couldn\'t share")}};';b=s.index(endm,a)+len(endm)
s=s[:a]+rd("2-sharing.js")+s[b:]
# 2b. canvas text uses the bundled font (Impact no longer needed) and long text is squeezed to fit
rep('fd=(getComputedStyle(document.body).getPropertyValue("--fd")||"").trim()||"Impact"','fd="ForgeCard"',1,"canvas font")
rep('x.fillText(o.t.toUpperCase().slice(0,22),540,1320)','x.fillText(o.t.toUpperCase().slice(0,22),540,1320,960)',1,"badge title")
rep('x.fillText(o.d,540,1410)','x.fillText(o.d,540,1410,960)',1,"badge desc")
rep('x.fillText(o.t.toUpperCase().slice(0,22),540,470)','x.fillText(o.t.toUpperCase().slice(0,22),540,470,960)',1,"workout title")
rep('x.fillText(String(r[0]),540,y)','x.fillText(String(r[0]),540,y,960)',1,"stat value")
rep('if(t)x.fillText(t,540,1680)','if(t)x.fillText(t,540,1680,960)',1,"pr line")
# 2c. badge emblem text inside the SVG image: bundled font + embed @font-face so it renders inside <img>
rep("Impact,'Arial Black',sans-serif","ForgeCard,'Arial Black',sans-serif",1,"svg font")
rep('const emImg=b=>new Promise(r=>{const i=new Image();i.onload=()=>r(i);i.src="data:image/svg+xml;charset=utf-8,"+encodeURIComponent(emb(b).replace("<svg ",\'<svg width="760" height="760" \'))});',
    'const emImg=b=>new Promise(r=>{const i=new Image(),ff=window.__FORGE_FONT?\'<defs><style>@font-face{font-family:"ForgeCard";src:url(\'+window.__FORGE_FONT+\') format("woff");font-weight:100 900}</style></defs>\':"";i.onload=()=>r(i);i.src="data:image/svg+xml;charset=utf-8,"+encodeURIComponent(emb(b).replace("<svg ",\'<svg width="760" height="760" \').replace(/(<svg[^>]*>)/,"$1"+ff))});',1,"emImg")
# 3. NativeBar bridge (phone notification bar) + getTodayStats
assert s.rstrip().endswith("</body></html>")
i=s.rindex("</body>")
s=s[:i]+rd("3-bar-and-nativebar.js")+s[i:]
open(dst,"w",encoding="utf-8").write(s)
print("patched ->",dst,len(s))
