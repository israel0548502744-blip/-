"""Build the final video: clips + overlay frames + voice + sound effects (+ optional music).
usage: python3 build.py <projdir> out.mp4 [--music song.mp3 --mvol 0.21 --mstart 0] [--skipbase]
<projdir>/plan.json: {"sections":[{"from":<beat index>,"pieces":[["/abs/clip.mp4",start_sec,available_sec],...]}, ...]}
A section runs from its beat's start to the next section's beat start (last one to the end).
Pieces play forward only, back to back, never repeating frames. If a section is longer than its
pieces, the last piece plays slightly slower to fill (keep it under ~5% by picking enough footage)."""
import json, subprocess, os, argparse
ap=argparse.ArgumentParser(); ap.add_argument('proj'); ap.add_argument('out'); ap.add_argument('--music'); ap.add_argument('--mvol',type=float,default=0.21)
ap.add_argument('--mstart',type=float,default=0.0); ap.add_argument('--skipbase',action='store_true'); ap.add_argument('--nostab',action='store_true')
ap.add_argument('--calm',action='store_true')   # tragedy stories: no phonk hit, no pops, no effect sounds
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
        stab=f'vidstabtransform=input={tf}:smoothing=20:maxshift=8:maxangle=0.004:optzoom=1,'   # capped: zoom never above ~3%
    # scale to cover 1080x1920 (works for any source aspect), best-quality settings
    # low-res sources (WhatsApp) get a light sharpen after upscaling
    h=int(subprocess.run(['ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=height','-of','csv=p=0',src],capture_output=True,text=True).stdout.strip() or 1920)
    w=int(subprocess.run(['ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=width','-of','csv=p=0',src],capture_output=True,text=True).stdout.strip() or 1080)
    low=h<1100 or w<720   # 576x1024 phone/TikTok downloads are low-res too
    pre='hqdn3d=2:1.5:4:3,' if low else ''   # clean noise before upscaling
    sharp='cas=0.7,eq=contrast=1.07:saturation=1.2:gamma=0.97,' if low else 'eq=contrast=1.04:saturation=1.1,'
    vf=stab+pre+f"setpts=PTS*{f:.4f},scale=1080:1920:force_original_aspect_ratio=increase:flags=lanczos,crop=1080:1920,"+sharp+z
    run(['ffmpeg','-y','-ss',str(ss),'-t',ind,'-i',src,'-vf',vf,'-t',f'{d:.3f}','-an','-c:v','libx264','-preset','slow','-crf','12',o])
  open(P+'seg/list.txt','w').write(''.join(f"file '{s}'\n" for s in segs))
  run(['ffmpeg','-y','-f','concat','-safe','0','-i',P+'seg/list.txt','-c','copy',P+'seg/base.mp4'])
print('shots',[(os.path.basename(s)[:12],ss,round(d,2),round(f,3)) for s,ss,d,f in shots],'duration',D)
# sounds: phonk hit on the hook, soft pop on match cards, soft coin on slips, sparkle on the logo end card
ev=[(0.0,'phonk',0.85)]+[(b['start'],'pop',0.55) for b in TL if b.get('m') or b.get('k')]+[(b['start']+(0.4 if b.get('m') else 0.05),'coin',0.6) for b in TL if b.get('s') or b.get('p')]+[(b['start'],'sparkle',0.5) for b in TL if b.get('logo')]+[(b['start'],'impact',0.55) for b in TL if b.get('big')]+[(b['start'],'pop',0.35) for b in TL if b.get('dt')]
_cur=None
for b in TL:   # scoreboard: pop when a new score appears
    if 'sc' in b:
        if b['sc'] and b['sc']!=_cur: ev.append((b['start'],'pop',0.5))
        _cur=b['sc'] or None
if a.calm: ev=[]
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
