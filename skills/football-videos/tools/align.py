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
