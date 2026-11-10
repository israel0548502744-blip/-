# Match image in our own style (design 5 "stadium lights", chosen 8.11): green, Hebrew names, time in the middle.
# usage: python3 matchcard.py out.png "<home he>" home_crest "<away he>" away_crest "20:30" "ראשון 8.11" "ליגת העל"
import sys,os,subprocess,tempfile,shutil
out,a,ai,b,bi,t,date,lg=sys.argv[1:9]
d=tempfile.mkdtemp(); here=os.path.dirname(os.path.abspath(__file__))
for f in (ai,bi): shutil.copy(f,d)
shutil.copy(os.path.join(here,'Heebo.ttf'),d)
css="""@font-face{font-family:H;src:url(Heebo.ttf)}*{margin:0;box-sizing:border-box}
body{width:1200px;height:675px;font-family:H;overflow:hidden;position:relative;color:#fff;background:radial-gradient(ellipse at 50% -10%,rgba(255,255,255,.28),transparent 45%),linear-gradient(180deg,#0a3a23,#05200f)}
.pitch{position:absolute;inset:0;background:repeating-linear-gradient(90deg,rgba(255,255,255,.035) 0 100px,transparent 100px 200px)}
.cc{position:absolute;left:450px;top:238px;width:300px;height:300px;border:3px solid rgba(255,255,255,.18);border-radius:50%}
.hl{position:absolute;left:599px;top:0;bottom:0;width:3px;background:rgba(255,255,255,.12)}
.row{position:absolute;left:0;right:0;top:170px;padding:0 40px;display:flex;justify-content:space-between;align-items:center;direction:ltr}
.team{width:420px;display:flex;flex-direction:column;align-items:center;gap:26px}
.cr img{width:250px;height:250px;object-fit:contain;filter:drop-shadow(0 14px 26px rgba(0,0,0,.6))}
.nm{font-weight:800;font-size:44px;text-align:center;line-height:1.05;text-shadow:0 4px 14px rgba(0,0,0,.6)}
.mid{display:flex;flex-direction:column;align-items:center}
.t{font-weight:900;font-size:84px;background:#062819;padding:0 22px;border-radius:20px}.d{font-weight:600;font-size:28px;color:#9fe8bd;margin-top:10px}
.lg{position:absolute;top:58px;width:100%;text-align:center;font-weight:700;font-size:28px;color:#d7f7e4}"""
h=f'<!doctype html><html dir="rtl"><head><meta charset="utf-8"><style>{css}</style></head><body><div class="pitch"></div><div class="cc"></div><div class="hl"></div><div class="lg">{lg}</div><div class="row"><div class="team"><div class="cr"><img src="{os.path.basename(ai)}"></div><div class="nm">{a}</div></div><div class="mid"><div class="t">{t}</div><div class="d">{date}</div></div><div class="team"><div class="cr"><img src="{os.path.basename(bi)}"></div><div class="nm">{b}</div></div></div></body></html>'
open(d+'/c.html','w').write(h)
js=f"const {{chromium}}=require('playwright');(async()=>{{const b=await chromium.launch();const p=await b.newPage({{viewport:{{width:1200,height:675}}}});await p.goto('file://{d}/c.html');await p.waitForTimeout(200);await p.screenshot({{path:'{os.path.abspath(out)}'}});await b.close();}})();"
open(d+'/s.js','w').write(js); subprocess.run(['node',d+'/s.js'],check=True); print('card',out)
