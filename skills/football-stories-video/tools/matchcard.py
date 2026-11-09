# Match image in our own style (design 6 "pitch", chosen 9.11 (design 5 kept as matchcard_d5.py)): green, Hebrew names, time in the middle.
# usage: python3 matchcard.py out.png "<home he>" home_crest "<away he>" away_crest "20:30" "ראשון 8.11" "ליגת העל"
import sys,os,subprocess,tempfile,shutil
out,a,ai,b,bi,t,date,lg=sys.argv[1:9]
d=tempfile.mkdtemp(); here=os.path.dirname(os.path.abspath(__file__))
for f in (ai,bi): shutil.copy(f,d)
shutil.copy(os.path.join(here,'Heebo.ttf'),d)
css="""@font-face{font-family:H;src:url(Heebo.ttf)}*{margin:0;box-sizing:border-box}
body{width:1200px;height:675px;font-family:H;overflow:hidden;position:relative;color:#fff;background:#0b1510}
.edge{position:absolute;inset:0;border-left:6px solid #38e07b;border-right:6px solid #38e07b;box-shadow:inset 14px 0 24px -12px rgba(56,224,123,.6),inset -14px 0 24px -12px rgba(56,224,123,.6)}
.box{position:absolute;left:46px;right:46px;top:0;bottom:0;border-radius:34px;background:radial-gradient(ellipse at 50% -10%,rgba(255,255,255,.28),transparent 45%),#14402a}
.pitch{position:absolute;left:84px;right:84px;top:40px;bottom:58px;border:7px solid #fff;border-radius:22px;overflow:hidden;
background:repeating-linear-gradient(90deg,#2bbf5c 0 85px,#25b052 85px 170px)}
.hl{position:absolute;left:50%;top:0;bottom:0;width:6px;margin-left:-3px;background:#fff}
.cc{position:absolute;left:50%;top:50%;width:300px;height:300px;margin:-150px 0 0 -150px;border:6px solid #fff;border-radius:50%}
.lg{position:absolute;top:30px;left:0;right:0;text-align:center}
.lg span{display:inline-block;background:#fff;color:#1d8a44;font-weight:800;font-size:26px;padding:2px 22px;border-radius:30px}
.row{position:absolute;left:0;right:0;top:0;bottom:0;padding:0 70px;display:flex;justify-content:space-between;align-items:center;direction:ltr}
.team{width:300px;display:flex;flex-direction:column;align-items:center;gap:14px}
.cr img{width:190px;height:190px;object-fit:contain;filter:drop-shadow(0 8px 14px rgba(0,0,0,.35))}
.nm{font-weight:900;font-size:40px;text-align:center;line-height:1.05;color:#fff;text-shadow:0 3px 10px rgba(0,0,0,.35)}
.mid{display:flex;flex-direction:column;align-items:center}
.t{font-weight:900;font-size:76px;line-height:1.1;background:#fff;color:#1d8a44;padding:0 24px;border-radius:18px;box-shadow:0 8px 20px rgba(0,0,0,.25)}
.d{font-weight:800;font-size:28px;color:#fff;margin-top:10px;text-shadow:0 2px 8px rgba(0,0,0,.35)}"""
h=f'<!doctype html><html dir="rtl"><head><meta charset="utf-8"><style>{css}</style></head><body><div class="edge"></div><div class="box"></div><div class="pitch"><div class="hl"></div><div class="cc"></div><div class="lg"><span>{lg}</span></div><div class="row"><div class="team"><div class="cr"><img src="{os.path.basename(ai)}"></div><div class="nm">{a}</div></div><div class="mid"><div class="t">{t}</div><div class="d">{date}</div></div><div class="team"><div class="cr"><img src="{os.path.basename(bi)}"></div><div class="nm">{b}</div></div></div></div></body></html>'
open(d+'/c.html','w').write(h)
js=f"const {{chromium}}=require('playwright');(async()=>{{const b=await chromium.launch();const p=await b.newPage({{viewport:{{width:1200,height:675}}}});await p.goto('file://{d}/c.html');await p.waitForTimeout(200);await p.screenshot({{path:'{os.path.abspath(out)}'}});await b.close();}})();"
open(d+'/s.js','w').write(js); subprocess.run(['node',d+'/s.js'],check=True); print('card',out)
