# Match image in our own style ("bright pitch", chosen 8.11 evening): striped grass, white pitch lines, Hebrew names, white time chip.
# usage: python3 matchcard.py out.png "<home he>" home_crest "<away he>" away_crest "20:30" "ראשון 8.11" "ליגת העל"
import sys,os,subprocess,tempfile,shutil
out,a,ai,b,bi,t,date,lg=sys.argv[1:9]
d=tempfile.mkdtemp(); here=os.path.dirname(os.path.abspath(__file__))
for f in (ai,bi): shutil.copy(f,d)
shutil.copy(os.path.join(here,'Heebo.ttf'),d)
css="""@font-face{font-family:H;src:url(Heebo.ttf)}*{margin:0;box-sizing:border-box}
body{width:1200px;height:675px;font-family:H;overflow:hidden;position:relative;color:#fff;background:repeating-linear-gradient(90deg,#1fae4b 0 100px,#27c257 100px 200px);box-shadow:inset 0 0 0 14px rgba(255,255,255,.9)}
.pitch{position:absolute;inset:0;background:radial-gradient(circle at 50% 55%,transparent 184px,rgba(255,255,255,.9) 186px 192px,transparent 194px),linear-gradient(90deg,transparent calc(50% - 4px),rgba(255,255,255,.9) calc(50% - 4px) calc(50% + 4px),transparent calc(50% + 4px))}
.row{position:absolute;left:36px;right:36px;top:112px;bottom:36px;display:flex;justify-content:space-between;align-items:center;direction:ltr}
.team{width:400px;display:flex;flex-direction:column;align-items:center;gap:24px}
.cr img{width:248px;height:248px;object-fit:contain;filter:drop-shadow(0 16px 28px rgba(0,0,0,.5))}
.nm{font-weight:800;font-size:50px;text-align:center;line-height:1.05;text-shadow:0 6px 16px rgba(0,50,20,.65)}
.mid{display:flex;flex-direction:column;align-items:center;gap:12px}
.t{font-weight:900;font-size:88px;line-height:1.2;background:#fff;color:#128a3a;padding:0 28px;border-radius:24px;box-shadow:0 12px 32px rgba(0,0,0,.25)}
.d{font-weight:800;font-size:30px;color:#fff;text-shadow:0 4px 12px rgba(0,0,0,.5)}
.lg{position:absolute;top:40px;left:50%;transform:translateX(-50%);font-weight:700;font-size:32px;color:#128a3a;background:#fff;padding:4px 32px;border-radius:999px;white-space:nowrap}"""
h=f'<!doctype html><html dir="rtl"><head><meta charset="utf-8"><style>{css}</style></head><body><div class="pitch"></div><div class="lg">{lg}</div><div class="row"><div class="team"><div class="cr"><img src="{os.path.basename(ai)}"></div><div class="nm">{a}</div></div><div class="mid"><div class="t">{t}</div><div class="d">{date}</div></div><div class="team"><div class="cr"><img src="{os.path.basename(bi)}"></div><div class="nm">{b}</div></div></div></body></html>'
open(d+'/c.html','w').write(h)
js=f"const {{chromium}}=require('playwright');(async()=>{{const b=await chromium.launch();const p=await b.newPage({{viewport:{{width:1200,height:675}}}});await p.goto('file://{d}/c.html');await p.waitForTimeout(200);await p.screenshot({{path:'{os.path.abspath(out)}'}});await b.close();}})();"
open(d+'/s.js','w').write(js); subprocess.run(['node',d+'/s.js'],check=True); print('card',out)
