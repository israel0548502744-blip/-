---
name: "football-tips-video"
description: "Build the user's daily vertical TikTok football betting-tips video (סרטון המלצות כדורגל) from his clips, match images, betting-slip screenshots and script, in his fixed house style."
---

# סרטוני המלצות כדורגל (Football tips video)

The user makes these videos daily. He sends: football clips, match images (two logos on a competition background: blue for Champions League, orange-black for Europa League), betting-slip screenshots (white background: pick + odds), the voiceover MP3 and the voiceover text. Claude builds the finished 9:16 video. Talk to him in short, simple, everyday Hebrew.

## Fixed rules (never break)
- Max length **40 seconds** (aim for 39.0s of voice + 0.7s tail).
- Always open with **"אל תפספסו את זה!!"** (or the exact opening line he writes, e.g. "אל תפספסו!"): huge white text with black outline + green glow, punch-zoom in, **impact** sound (deep boom + shimmer).
- **The first 2 seconds decide everything.** Frame 0 = the most energetic, sharp, colorful moment he sent (players jumping/celebrating, a goal hitting the net) — never a blurry close-up or a slow wide tactical shot. Cut to a second dynamic shot by ~1.2s (when the intro line starts).
- **Match image** pops in center-top **the moment the voice says "X נגד Y"**, with a soft bubbly **pop** (he disliked the whoosh noise there). Stays ~1.6s minimum.
- **Slip image** pops in **when the voice starts the pick sentence** ("ההמלצה שלי", "אני הולך על", "נלך על"), with a **ka-ching** sound, and **stays until the end of that sentence**. Show the full image as sent, **at ~80% of the screen width (not edge to edge — he said full-width is too big)**, framed with a green border + glow and the **יום המזל logo badge** on its top-left corner. Make that sentence its own `tip` beat. If the pick sentence also introduces the match ("ההמלצה שלי לקלאסיקו"), put `m` and `s` on the same tip beat; the slip then comes 0.35s after the card.
- **WhatsApp line** ("אל תשכחו להיכנס לקבוצת הוואטסאפ...") gets a green card "קבוצת הוואטסאפ שלנו / הקישור בביו".
- **Captions** (style learned from his reference edit, 25.10): Heebo ExtraBold 66px, white with a soft dark shadow (no thick outline), **in the middle of the screen** (top 930px) between the match card and the slip, **max 3 words per chunk, the whole chunk appears at once** (80ms fade), no jumping. Pick words green #38e07b (`hl`); a brand/name word can be cyan #3ec6ff (`hl2`, set per word in timeline). Slip sits lower (top 1110px) so it never covers captions.
- **No zoom on clips. No repeated frames / backward jumps. No shaking**: each clip plays forward continuously; cut only between different clips or forward-only to skip a flash or a logo outro; stabilize with vidstab.
- Skip white flash frames and end-of-clip logo cards inside source clips (YAVG > 165; check the contact sheet for logo outros).
- **Best quality always** (background footage quality is what keeps viewers): lanczos scaling, light unsharp on low-res sources, x264 slow CRF 12 for segments, CRF 15 final, AAC 320k. If his clips are low-res (WhatsApp-compressed), tell him once to send originals.
- **Music**: deliver ONE version **without background music**; he adds the song in TikTok (his pick: Metamorphosis – INTERWORLD, 21% under the voice). Claude cannot download songs (network is blocked). Only add music if he sends the audio file: `--music file --mvol 0.21`.
- No "18+" line unless he asks.
- When unsure about anything that changes the result (which clip is which team, missing clip, wording), ask before building. Never guess a team from a clip: check jerseys/logos frame by frame with timestamps, and ask if unclear.

## Brand: יום המזל (YOM HAMZEL)
All videos are for his TikTok page **יום המזל** (clover, gold coins, green). Make every video recognisably his:
- logo badge on every slip, **logo end card** on the last beat (`"logo":true` → big logo pops with a sparkle chime, gentle pulse), green #38e07b accents (slip border/glow, hook glow, pick words).
- The logo file is `assets/logo.png` next to this SKILL.md (round, transparent). Copy it to `/home/claude/fv/tools/logo.png` at setup and into each `<proj>/img/`.

## Editing style (learned from his reference videos, 25.10 — his own best + competitor "הכריש")
- **Fast pace**: a new shot every ~1.5–2.5s (competitors cut every ~1.5–2s). Use several forward-only pieces per section (different moments/clips), never one long shot of 10s+. Cut on phrase breaks.
- **B-roll matches the words**: goal/net shot on "שערים"/"כובשות", celebration on the pick, crowd/fans on "משחק פתוח", close-up face on emotional lines. Close-ups and celebrations beat wide tactical shots.
- **Strong first frame**: hook on a close-up face or big celebration, caption visible from frame 1.
- **Comment CTA works**: when he writes one ("תכתבו בתגובות ... ואשלח קישור"), keep it as its own beat near the end.
- Competitors zoom in hard on faces — he wants **no heavy zoom**; keep our no-zoom rule.
- Sound design: pleasant, punchy, never hissy noise. Effects: impact (hook), pop (match card), ka-ching (slip), sparkle (logo end card).
- His reference was 64s; his own tips videos stay ≤40s (fixed rule).

## His saved choices (control page)
Control page artifact: https://claude.ai/artifact/8zmzrbfAPGTgjxvBUFEysS (db doc `studio/v2`, read with ArtifactData get collection `studio` doc `v2`). Current picks: punch-zoom hook, whoosh on hook only (match image is silent), ka-ching on slip, slip pops in full image, WhatsApp green card, male energetic voice, speed-up if over 40s, Heebo Black, 3-word chunk captions (whole chunk at once), green highlight, middle of screen, no transition sound, no 18+. If he changes a choice there, read it before building.

## Workflow
1. **Collect** clips, match images, slip images, text, voice MP3. Map each image to its match (by the names on the slip). He usually tells you which clip is which match and where the last pick goes.
2. **Inspect clips**: per-second contact sheet with timestamps
   `ffmpeg -i clip.mp4 -vf "fps=1,scale=160:-1,drawtext=text='%{pts\:hms}':x=4:y=4:fontsize=14:fontcolor=yellow:box=1:boxcolor=black,tile=10x3" -frames:v 1 sheet.jpg`
   Identify team per clip and good moments (goal, celebration, logo outro to cut). Check md5 of uploads (he may resend the same file). Find flash frames:
   `ffmpeg -i clip.mp4 -vf "signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=-" -f null - | paste - - | sed -E 's/.*pts_time:([0-9.]+).*YAVG=([0-9.]+)/\1 \2/' | awk '$2>165'`
3. **Script** → `<proj>/script.json`: list of beats `{"type": hook|line|match|tip|outro, "vo": text, "hl": words to color green, "m": match image file, "s": slip image file, "wa": true}`. Remove emojis. Fix only small spelling (כשמדובר, להיכנס, וואטסאפ, ברוז'). The text must be what the voice actually says, in order. Punctuation matters: every `, : . ! ?` is a phrase break used for timing.
   Example: `[{"type":"hook","vo":"אל תפספסו את זה!!"},{"type":"line","vo":"אלו ההמלצות שלי למשחקים היום!"},{"type":"match","vo":"באיירן מינכן נגד ארסנל","m":"m-bayern.jpg"},{"type":"line","vo":"..."},{"type":"tip","vo":"ההמלצה שלי: באיירן מינכן או תיקו, ושתי הקבוצות כובשות","hl":"באיירן מינכן או תיקו, ושתי הקבוצות כובשות","s":"s-bayern.jpg"},{"type":"line","vo":"רק שנייה לפני ההמלצה הבאה, אל תשכחו להיכנס לקבוצת הוואטסאפ שלנו ...","hl":"קבוצת הוואטסאפ","wa":true},{"type":"outro","vo":"בהצלחה לכולם!","hl":"בהצלחה לכולם!"}]`
4. **Voice**: usually he sends the ElevenLabs MP3 himself. If he asks Claude to make it (ElevenLabs connector): `creative_create_flow`, then `creative_generate_speech` with model `eleven_v4`, voice **Itai – Upbeat Social Creator `JIxTgeeS5w0UQyBxEnrl`** (Hebrew), prompt = `[excited] [shouting] <hook> [excited] <rest of text>`. The audio URL is on storage.googleapis.com, which is blocked here, so give him the flow link and ask him to download the MP3 and attach it (his free tier was flagged once; if generation fails, tell him plainly). Save it as `<proj>/vo_raw.mp3`.
5. **Set up** (once per session): write the tool files below into `/home/claude/fv/tools/`, run `python3 sfx.py` there, copy `assets/logo.png` there too. Font: `curl -sSL -o <proj>/fonts/Heebo.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/heebo/Heebo%5Bwght%5D.ttf"` (raw.githubusercontent works; github.com/raw does not). Project dir: `/home/claude/fv/<name>/` with `script.json`, `plan.json`, `img/` (match + slip images), `fonts/Heebo.ttf`, `vo_raw.mp3`, and a copy of `overlay.html`.
6. **plan.json** – which clip plays under which part. A section starts at a beat index and runs to the next section; pieces are `[path, start_sec, available_sec]` and play forward back-to-back:
   `{"sections":[{"from":0,"pieces":[[celebration_clip,12.5,1.3]]},{"from":1,"pieces":[[teamA_clip,0,30]]},{"from":5,"pieces":[[teamB_clip,0,12.1],[teamB_clip,13.7,5.0]]},{"from":10,"pieces":[[teamC_clip,12,15.5]]}]}`
   Hook = strongest celebration (take it from a part of a clip that won't be used again). Each match section uses that match's clip from its "X נגד Y" beat; the WhatsApp line can keep running the previous match's clip. Keep slowdown under ~5% (give enough footage).
7. **Run**:
   `python3 tools/align.py <proj> 39` (maps phrases to voiced parts by letter-rate fit: a phrase may span several parts when the voice pauses mid-sentence, or phrases may share a part when it skips a comma; prints the span/share cases and fit cost — check each phrase's time looks plausible)
   `node tools/render.js <proj>/timeline.json <proj>/ov <end+0.7> 30`
   `python3 tools/build.py <proj> <proj>/video_final.mp4` — at best quality this takes ~10 minutes: run it with `run_in_background` and tell him it's rendering.
8. **Check before sending**: grid of frames at hook, each match card, each slip, WhatsApp card, outro (`ffmpeg -ss T -frames:v 1`, hstack, Read the image). Confirm duration ≤ 40, no flash frames, captions readable, cards on time. Then SendUserFile one version without music, and list in one short message what's in it and what to check (e.g. voice speed-up factor).
   Tell him once (short) how to keep TikTok quality: move the file to the phone directly (download from here / Drive, not through WhatsApp, which re-compresses); in TikTok turn on **Upload HD** (More options → Upload quality / "העלאה באיכות HD"); don't add TikTok filters or re-trim inside the app (re-encodes).
9. He replies with fixes; apply and re-render (`--skipbase` reuses clips if only overlay/audio changed).

## Tool files

### tools/align.py
```python
"""Tighten pauses, speed voiceover to fit the max length, build timeline.json.
usage: python3 align.py <projdir> [max_seconds=39]
Needs <projdir>/script.json and <projdir>/vo_raw.mp3 (or .wav).
Each beat's text is split into phrases at , : . ! ? ; every pause in the recording should match a phrase break.
If the counts don't match it tries other silence settings, then falls back to proportional timing."""
import json, re, subprocess, sys, os, numpy as np, wave
P=sys.argv[1].rstrip('/')+'/'; TARGET=float(sys.argv[2]) if len(sys.argv)>2 else 39.0; GAP=0.10
S=json.load(open(P+'script.json'))
clean=lambda s: re.sub(r'[.,:!?]','',s)
PH=[]
for bi,b in enumerate(S):
    cur=[]
    for w in b['vo'].split():
        cur.append(w)
        if re.search(r'[,:.!?]$',w): PH.append((bi,' '.join(cur))); cur=[]
    if cur: PH.append((bi,' '.join(cur)))
src=P+('vo_raw.mp3' if os.path.exists(P+'vo_raw.mp3') else 'vo_raw.wav')
subprocess.run(['ffmpeg','-y','-v','error','-i',src,'-ac','1','-ar','48000',P+'vo_mono.wav'],check=True)
w=wave.open(P+'vo_mono.wav'); SR=w.getframerate(); x=np.frombuffer(w.readframes(w.getnframes()),np.int16).astype(np.float32); dur=len(x)/SR
def voiced_for(n,d):
    out=subprocess.run(['ffmpeg','-i',P+'vo_mono.wav','-af',f'silencedetect=n={n}dB:d={d}','-f','null','-'],capture_output=True,text=True).stderr
    st=[float(v) for v in re.findall(r'silence_start: ([\d.]+)',out)]; en=[float(v) for v in re.findall(r'silence_end: ([\d.]+)',out)]
    vo=[]; p=0.0
    for a,e in zip(st,en+[dur]*(len(st)-len(en))):
        if a>p+0.03: vo.append((p,a))
        p=e
    if p<dur-0.03: vo.append((p,dur))
    return vo
L=[sum(max(1,len(clean(q))) for q in txt.split()) for _,txt in PH]
N=len(PH)
def fit(v):
    # map phrases onto voiced parts: a phrase may span several parts (voice paused mid-sentence)
    # or several phrases may share one part (voice skipped a comma pause). DP on letter-rate fit.
    K=len(v); rate=(v[-1][1]-v[0][0])/sum(L); INF=float('inf')
    dp=[[INF]*(K+1) for _ in range(N+1)]; bk=[[None]*(K+1) for _ in range(N+1)]; dp[0][0]=0
    for p in range(N):
        for i in range(K):
            if dp[p][i]==INF: continue
            for j in range(i+1,min(K,i+6)+1):          # phrase p spans parts i..j-1
                d=v[j-1][1]-v[i][0]; c=dp[p][i]+((L[p]*rate-d)/d)**2+0.15*(j-i-1)
                if c<dp[p+1][j]: dp[p+1][j]=c; bk[p+1][j]=(p,i,'span')
            for q in range(p+2,min(N,p+4)+1):          # phrases p..q-1 share part i
                d=v[i][1]-v[i][0]; c=dp[p][i]+((sum(L[p:q])*rate-d)/d)**2+0.15*(q-p-1)
                if c<dp[q][i+1]: dp[q][i+1]=c; bk[q][i+1]=(p,i,'share')
    if dp[N][K]==INF: return INF,None
    out=[]; p,j=N,K
    while p>0:
        pp,i,kind=bk[p][j]; out.append((pp,p,i,j,kind)); p,j=pp,i
    return dp[N][K]/N,out[::-1]
best=None
for n in (-38,-35,-40,-32,-42,-45):
    for d in (0.12,0.10,0.15,0.08,0.18,0.22):
        v=[iv for iv in voiced_for(n,d) if iv[1]-iv[0]>0.08]
        if not v: continue
        c,m=fit(v)
        if m and (best is None or c<best[0]): best=(c,v,m,n,d)
cost,voiced,MAP,bn,bd=best
print(f'phrases {N}  voiced parts {len(voiced)}  silence {bn}dB/{bd}s  fit cost {cost:.3f}')
pad=0.03; parts=[]; partpos=[]; t=0.0
for i,(a,e) in enumerate(voiced):
    a2=max(0,a-pad); e2=min(dur,e+pad); seg=x[int(a2*SR):int(e2*SR)]
    partpos.append((t+(a-a2), t+(a-a2)+(e-a))); parts.append(seg); t+=len(seg)/SR
    if i<len(voiced)-1: parts.append(np.zeros(int(GAP*SR),np.float32)); t+=GAP
y=np.concatenate(parts); tight=len(y)/SR; tempo=max(1.0,tight/TARGET)
wo=wave.open(P+'vo_tight.wav','wb'); wo.setnchannels(1); wo.setsampwidth(2); wo.setframerate(SR); wo.writeframes(y.astype(np.int16).tobytes()); wo.close()
subprocess.run(['ffmpeg','-y','-v','error','-i',P+'vo_tight.wav','-af',f'atempo={tempo:.4f}','-ar','48000',P+'vo_final.wav'],check=True)
print(f'raw {dur:.2f}s  tight {tight:.2f}s  speed x{tempo:.3f}  final {tight/tempo:.2f}s')
newpos=[]
for p,q,i,j,kind in MAP:
    a,e=partpos[i][0],partpos[j-1][1]
    if kind=='span': newpos.append((a,e))
    else:
        tot=sum(L[p:q]); c=0
        for r in range(p,q): s0=a+(e-a)*c/tot; c+=L[r]; newpos.append((s0,a+(e-a)*c/tot))
    if kind=='share' or j-i>1: print(' ',kind,j-i,'parts:',' | '.join(PH[r][1] for r in range(p,q))[:60])
beats=[dict(b,words=[],start=None,end=None,phrases=[]) for b in S]
for (bi,txt),(a,e) in zip(PH,newpos):
    a/=tempo; e/=tempo; ws=txt.split(); L=sum(max(1,len(clean(q))) for q in ws); tt=a
    hl=set(clean(q) for q in S[bi].get('hl','').split()); ph=[]
    for q in ws: ph.append({'w':q,'t':round(tt,3),'hl':clean(q) in hl}); tt+=(e-a)*max(1,len(clean(q)))/L
    B=beats[bi]; B['phrases'].append({'start':round(a,3),'end':round(e,3),'words':ph}); B['words']+=ph
    B['start']=round(a,3) if B['start'] is None else B['start']; B['end']=round(e,3)
json.dump(beats,open(P+'timeline.json','w'),ensure_ascii=False,indent=1)
for b in beats: print(f"{b['start']:6.2f}-{b['end']:6.2f} {b['type']:6} {b['vo'][:40]}")
```

### tools/render.js
```javascript
// node render.js <timeline.json> <outdir> <duration> [fps]
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const [tlPath, outDir, durS, fpsS] = process.argv.slice(2);
  const fps = +(fpsS || 30), dur = +durS, N = Math.ceil(dur * fps);
  fs.rmSync(outDir, { recursive: true, force: true }); fs.mkdirSync(outDir, { recursive: true });
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
  // overlay.html is copied into the project dir so img/ and fonts/ resolve there
  await p.goto('file://' + path.resolve(path.dirname(tlPath), 'overlay.html'));
  await p.evaluate(tl => setTL(tl), JSON.parse(fs.readFileSync(tlPath, 'utf8')));
  await p.evaluate(() => document.fonts.ready);
  // preload images
  const imgs = [...new Set(JSON.parse(fs.readFileSync(tlPath, 'utf8')).flatMap(b => [b.m, b.s].filter(Boolean)).concat(['logo.png']))];
  await p.evaluate(list => Promise.all(list.map(n => new Promise(r => { const i = new Image(); i.onload = i.onerror = r; i.src = 'img/' + n; }))), imgs);
  if (process.env.WA_SUB) await p.evaluate(v => { WA_SUB = v; }, process.env.WA_SUB);
  let prevKey = null, prevFile = null, shots = 0;
  for (let f = 0; f < N; f++) {
    const key = await p.evaluate(t => render(t), f / fps);
    const file = path.join(outDir, `ov_${String(f).padStart(5, '0')}.png`);
    if (key === prevKey && prevFile) fs.linkSync(prevFile, file);
    else { await p.screenshot({ path: file, omitBackground: true }); prevKey = key; prevFile = file; shots++; }
  }
  await b.close(); console.log(`frames ${N}, screenshots ${shots}`);
})();
```

### tools/overlay.html
```html
<!doctype html><html dir="rtl"><head><meta charset="utf-8">
<style>
@font-face{font-family:H;src:url(fonts/Heebo.ttf);font-weight:100 900}
html,body{margin:0;width:1080px;height:1920px;background:transparent;overflow:hidden;font-family:H,sans-serif}
#stage{position:absolute;inset:0}
.hook{position:absolute;left:40px;right:40px;top:0;bottom:0;display:flex;align-items:center;justify-content:center;text-align:center;
  font-weight:900;font-size:150px;line-height:1.05;color:#fff;-webkit-text-stroke:14px #000;paint-order:stroke fill;
  text-shadow:0 0 38px rgba(56,224,123,.75),0 10px 30px rgba(0,0,0,.6)}
.cap{position:absolute;left:0;right:0;top:930px;display:flex;justify-content:center;white-space:nowrap;font-weight:800;font-size:66px;line-height:1.2;color:#fff;
  text-shadow:0 0 3px #000,0 2px 4px rgba(0,0,0,.95),0 4px 14px rgba(0,0,0,.7)}
.cap .ln{transform-origin:50% 50%}.cap span{margin:0 9px;display:inline-block}
.cap .hl{color:#38e07b}.cap .hl2{color:#3ec6ff}
.card{position:absolute;left:60px;right:60px;top:250px;border-radius:22px;overflow:hidden;box-shadow:0 24px 60px rgba(0,0,0,.65);transform-origin:50% 50%}
.card img{display:block;width:100%}
.slipw{position:absolute;left:110px;right:110px;top:1110px;transform-origin:50% 50%}
.slip{border-radius:14px;overflow:hidden;border:5px solid #38e07b;box-shadow:0 0 34px rgba(56,224,123,.55),0 22px 50px rgba(0,0,0,.6)}
.slip img{display:block;width:100%}
.badge{position:absolute;left:-52px;top:-62px;width:150px;height:150px;filter:drop-shadow(0 8px 14px rgba(0,0,0,.6))}
.logo{position:absolute;left:300px;width:480px;top:370px;transform-origin:50% 50%;filter:drop-shadow(0 0 40px rgba(56,224,123,.65)) drop-shadow(0 20px 40px rgba(0,0,0,.6))}
.wa{position:absolute;left:80px;right:80px;top:560px;background:#25d366;border-radius:40px;padding:34px 40px;display:flex;align-items:center;gap:30px;
  box-shadow:0 22px 50px rgba(0,0,0,.55);transform-origin:50% 50%;color:#06301a}
.wa svg{flex:none;width:130px;height:130px}
.wa b{display:block;white-space:nowrap;font-size:58px;font-weight:900;line-height:1.05}
.wa small{display:block;font-size:44px;font-weight:700;opacity:.85;margin-top:8px}
</style></head><body><div id="stage"></div>
<script>
let TL=null;
const stage=document.getElementById('stage');
function pop(p){ // 0..1 -> scale with overshoot
  if(p>=1)return 1; if(p<=0)return .55; const s=1.70158*1.3,x=p-1; return .55+.45*(1+(s+1)*x*x*x+s*x*x)}
let CH=[];let WA_SUB="הקישור בביו";
function setTL(tl){TL=tl;CH=[];
  TL.forEach((b,bi)=>{if(bi===0)return;(b.phrases||[{words:b.words,end:b.end}]).forEach(ph=>{
    const ws=ph.words,n=Math.ceil(ws.length/3),size=Math.ceil(ws.length/n);   // balanced chunks of <=3 words
    for(let i=0;i<ws.length;i+=size){const part=ws.slice(i,i+size);const last=i+size>=ws.length;
      CH.push({words:part,t0:part[0].t,t1:last?ph.end:ws[i+size].t})}})})}
function render(t){
  let html='',key='';
  const hb=TL[0];
  if(t<hb.end+0.05){const p=Math.min(1,t/0.42);const sc=p<1?2.4-1.4*(1-Math.pow(1-p,3)):1;const sc2=p<1?sc:(1+0.03*Math.sin((t-0.42)*6));
    html+=`<div class="hook" style="transform:scale(${sc2.toFixed(3)});opacity:${Math.min(1,p*2.5).toFixed(2)}">${hb.vo}</div>`;key+='h'+sc2.toFixed(3)}
  for(const b of TL){
    if(b.type==='hook')continue;
    if(b.m){const s=b.start,e=Math.max(b.end,s+1.6);if(t>=s&&t<e){const sc=pop((t-s)/0.35);
      html+=`<div class="card" style="transform:scale(${sc.toFixed(3)})"><img src="img/${b.m}"></div>`;key+='m'+b.m+sc.toFixed(3)}}
    if(b.s){const s=b.start+(b.m?0.35:0),e=b.end+0.25;if(t>=s&&t<e){const sc=pop((t-s)/0.35);const bs=pop((t-s-0.15)/0.3);
      html+=`<div class="slipw" style="transform:scale(${sc.toFixed(3)})"><div class="slip"><img src="img/${b.s}"></div><img class="badge" src="img/logo.png" style="transform:scale(${bs.toFixed(3)}) rotate(${(-8+8*Math.min(1,(t-s)/0.5)).toFixed(1)}deg)"></div>`;key+='s'+b.s+sc.toFixed(3)+bs.toFixed(3)+Math.min(1,(t-s)/0.5).toFixed(2)}}
    if(b.logo){const s=b.start;if(t>=s){const sc=pop((t-s)/0.4);const br=1+0.025*Math.sin((t-s)*5);
      html+=`<img class="logo" src="img/logo.png" style="transform:scale(${(sc*br).toFixed(3)})">`;key+='L'+(sc*br).toFixed(3)}}
    if(b.wa){const s=b.start+0.15,e=b.end;if(t>=s&&t<e){const sc=pop((t-s)/0.35);
      html+=`<div class="wa" style="transform:scale(${sc.toFixed(3)})"><svg viewBox="0 0 100 100"><circle cx="50" cy="48" r="40" fill="#fff"/><path d="M22 86 L30 64 L44 78 Z" fill="#fff"/>
      <path d="M38 30c2-2 5-2 6 1l3 7c1 2 0 4-2 5l-2 2c2 5 6 9 11 11l2-2c1-2 3-3 5-2l7 3c3 1 3 4 1 6l-3 3c-4 4-14 1-23-8s-12-19-8-23z" fill="#25d366"/></svg>
      <div><b>קבוצת הוואטסאפ שלנו</b><small>${WA_SUB}</small></div></div>`;key+='w'+sc.toFixed(3)}}
  }
  // captions: whole line is laid out up front, words are only revealed -> nothing ever moves
  let ci=-1;for(let k=0;k<CH.length;k++)if(t>=CH[k].t0)ci=k;
  if(ci>=0){const c=CH[ci];const nxt=ci+1<CH.length?CH[ci+1].t0:1e9;const hide=Math.min(nxt,c.t1+0.45);
    if(t<hide){let rk='';
      // whole chunk appears at once (reference style), quick 80ms fade
      const o=Math.max(0,Math.min(1,(t-c.t0)/0.08));rk=o.toFixed(1);
      const sp=c.words.map(w=>`<span class="${w.hl?'hl':w.hl2?'hl2':''}">${w.w}</span>`).join(' ');
      html+=`<div class="cap" style="opacity:${o.toFixed(2)}"><div class="ln" id="cap">${sp}</div></div>`;key+='c'+ci+'_'+rk}}
  stage.innerHTML=html;
  const cap=document.getElementById('cap');if(cap){const w=cap.scrollWidth;if(w>940)cap.style.transform=`scale(${(940/w).toFixed(3)})`}
  return key}
</script></body></html>
```

### tools/build.py
```python
"""Build the final video: clips + overlay frames + voice + sound effects (+ optional music).
usage: python3 build.py <projdir> out.mp4 [--music song.mp3 --mvol 0.21 --mstart 0] [--skipbase]
<projdir>/plan.json: {"sections":[{"from":<beat index>,"pieces":[["/abs/clip.mp4",start_sec,available_sec],...]}, ...]}
A section runs from its beat's start to the next section's beat start (last one to the end).
Pieces play forward only, back to back, never repeating frames. If a section is longer than its
pieces, the last piece plays slightly slower to fill (keep it under ~5% by picking enough footage)."""
import json, subprocess, os, argparse
ap=argparse.ArgumentParser(); ap.add_argument('proj'); ap.add_argument('out'); ap.add_argument('--music'); ap.add_argument('--mvol',type=float,default=0.21)
ap.add_argument('--mstart',type=float,default=0.0); ap.add_argument('--skipbase',action='store_true'); ap.add_argument('--nostab',action='store_true')
a=ap.parse_args(); P=os.path.abspath(a.proj)+'/'; T=os.path.dirname(os.path.abspath(__file__))+'/'
os.makedirs(P+'seg',exist_ok=True)
TL=json.load(open(P+'timeline.json')); PL=json.load(open(P+'plan.json'))
D=round(TL[-1]['end']+0.7,2)
def run(c): subprocess.run(c,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
secs=PL['sections']; bounds=[0.0 if s['from']==0 else TL[s['from']]['start'] for s in secs]+[D]
shots=[]
for i,s in enumerate(secs):
    left=bounds[i+1]-bounds[i]
    for k,(src,ss,avail) in enumerate(s['pieces']):
        last=k==len(s['pieces'])-1; d=left if last else min(avail,left); f=max(1.0,d/avail)
        shots.append((src,ss,d,f)); left-=d
        if left<=0.01: break
z="setsar=1,fps=30,format=yuv420p"   # no zoom, ever
if not a.skipbase:
  segs=[]
  for i,(src,ss,d,f) in enumerate(shots):
    o=P+f'seg/s{i:02d}.mp4'; segs.append(o); tf=P+f'seg/t{i:02d}.trf'; ind=f'{d/f+0.1:.3f}'
    stab=''
    if not a.nostab:
        run(['ffmpeg','-y','-ss',str(ss),'-t',ind,'-i',src,'-vf',f'vidstabdetect=shakiness=6:accuracy=12:result={tf}','-f','null','-'])
        stab=f'vidstabtransform=input={tf}:smoothing=20:optzoom=1,'
    # scale to cover 1080x1920 (works for any source aspect), best-quality settings
    # low-res sources (WhatsApp) get a light sharpen after upscaling
    h=int(subprocess.run(['ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=height','-of','csv=p=0',src],capture_output=True,text=True).stdout.strip() or 1920)
    sharp='unsharp=5:5:0.7:5:5:0,' if h<1000 else ''
    vf=stab+f"setpts=PTS*{f:.4f},scale=1080:1920:force_original_aspect_ratio=increase:flags=lanczos,crop=1080:1920,"+sharp+z
    run(['ffmpeg','-y','-ss',str(ss),'-t',ind,'-i',src,'-vf',vf,'-t',f'{d:.3f}','-an','-c:v','libx264','-preset','slow','-crf','12',o])
  open(P+'seg/list.txt','w').write(''.join(f"file '{s}'\n" for s in segs))
  run(['ffmpeg','-y','-f','concat','-safe','0','-i',P+'seg/list.txt','-c','copy',P+'seg/base.mp4'])
print('shots',[(os.path.basename(s)[:12],ss,round(d,2),round(f,3)) for s,ss,d,f in shots],'duration',D)
# sounds: impact on the hook, soft pop on match cards, ka-ching on slips, sparkle on the logo end card
ev=[(0.0,'impact',0.8)]+[(b['start'],'pop',0.55) for b in TL if b.get('m')]+[(b['start']+(0.4 if b.get('m') else 0.05),'kaching',0.75) for b in TL if b.get('s')]+[(b['start'],'sparkle',0.5) for b in TL if b.get('logo')]
ins=['-i',P+'seg/base.mp4','-framerate','30','-i',P+'ov/ov_%05d.png','-i',P+'vo_final.wav']; fl=['[2:a]volume=1.0,apad[vo]']; mix=['[vo]']; n=3
if a.music:
    ins+=['-ss',str(a.mstart),'-stream_loop','-1','-i',a.music]; fl.append(f'[{n}:a]volume={a.mvol},afade=t=in:d=0.3,afade=t=out:st={D-1.2}:d=1.2[mu]'); mix.append('[mu]'); n+=1
for j,(t,name,v) in enumerate(ev):
    ins+=['-i',T+f'sfx/{name}.wav']; ms=max(0,int(t*1000)); fl.append(f'[{n}:a]volume={v},adelay={ms}|{ms}[e{j}]'); mix.append(f'[e{j}]'); n+=1
fl.append(f"{''.join(mix)}amix=inputs={len(mix)}:normalize=0:duration=longest,atrim=0:{D},alimiter=limit=0.95[aout]")
fl.insert(0,'[0:v][1:v]overlay=0:0:shortest=0,format=yuv420p[vout]')
run(['ffmpeg','-y',*ins,'-filter_complex',';'.join(fl),'-map','[vout]','-map','[aout]','-t',str(D),'-c:v','libx264','-preset','slow','-crf','15','-profile:v','high',
     '-c:a','aac','-b:a','320k','-movflags','+faststart',a.out])
print('done',a.out)
```

### tools/sfx.py
```python
import numpy as np, wave, sys, os
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),'sfx'); os.makedirs(OUT,exist_ok=True)
SR=48000
def save(name,x):
    x=np.clip(x/ (np.max(np.abs(x))+1e-9)*0.9,-1,1)
    d=(x*32767).astype(np.int16); d=np.stack([d,d],1)
    w=wave.open(name,'wb');w.setnchannels(2);w.setsampwidth(2);w.setframerate(SR);w.writeframes(d.tobytes());w.close()
def env(n,a,dec):
    t=np.arange(n)/SR; e=np.minimum(t/a,1)*np.exp(-t/dec); return e
rng=np.random.default_rng(1)
def bandnoise(dur,f1,f2,q=2.0):
    n=int(SR*dur); x=rng.standard_normal(n); out=np.zeros(n)
    # time-varying resonant bandpass (state variable filter)
    low=band=0.0
    fr=np.geomspace(f1,f2,n)
    for i in range(n):
        f=2*np.sin(np.pi*fr[i]/SR); hp=x[i]-low-band/q; band+=f*hp; low+=f*band; out[i]=band
    return out
# whoosh: rising then falling band noise, swell envelope
n=int(SR*.6); t=np.arange(n)/SR
w=bandnoise(.6,350,3500,1.5)*np.sin(np.pi*t/.6)**2
save(OUT+'/whoosh.wav',w)
# ka-ching: drawer clunk + two bright bell hits
n=int(SR*1.4); t=np.arange(n)/SR; x=np.zeros(n)
clunk=rng.standard_normal(int(SR*.07))*np.exp(-np.arange(int(SR*.07))/SR/.015); x[:len(clunk)]+=0.6*np.convolve(clunk,np.ones(8)/8,'same')
for st,f in [(0.06,2093),(0.16,2637)]:
    i=int(st*SR); tt=t[:n-i]
    b=sum(a*np.sin(2*np.pi*f*m*tt) for m,a in [(1,1),(2.01,.5),(3.02,.25),(4.1,.12)])*np.exp(-tt/.35)
    x[i:]+=b
save(OUT+'/kaching.wav',x)
# pop: soft bubbly pop for the match card (pleasant, short, no noise hiss)
n=int(SR*.35); t=np.arange(n)/SR
f=280+700*np.exp(-t/.025); ph=2*np.pi*np.cumsum(f)/SR
p=np.sin(ph)*np.exp(-t/.06)
p+=0.35*np.sin(2*np.pi*1760*t)*np.exp(-t/.09)+0.2*np.sin(2*np.pi*2640*t)*np.exp(-t/.07)
save(OUT+'/pop.wav',p)
# impact: deep cinematic boom + bright shimmer for the hook (frame 0)
n=int(SR*1.6); t=np.arange(n)/SR
f=40+90*np.exp(-t/.05); boom=np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t/.45)
hit=rng.standard_normal(n)*np.exp(-t/.03); hit=np.convolve(hit,np.ones(30)/30,'same')
sh=sum(np.sin(2*np.pi*fq*t+k)*np.exp(-t/.5) for k,fq in enumerate([1318,1975,2637,3951]))*0.12*np.minimum(t/.01,1)
save(OUT+'/impact.wav',boom+0.5*hit+sh)
# sparkle: lucky chime for the logo end card
n=int(SR*1.2); t=np.arange(n)/SR; x=np.zeros(n)
for k,(st,fq) in enumerate([(0,1568),(.07,2093),(.14,2637),(.21,3136)]):
    i=int(st*SR); tt=t[:n-i]; x[i:]+=np.sin(2*np.pi*fq*tt)*np.exp(-tt/.3)*(0.9-0.12*k)
save(OUT+'/sparkle.wav',x)
```