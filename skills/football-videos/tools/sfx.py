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
# phonk hit: distorted 808 drop + punchy kick + clap + down-sweep (cool, no bells) for the hook
n=int(SR*1.3); t=np.arange(n)/SR
f=48+70*np.exp(-t/.04); b808=np.tanh(2.8*np.sin(2*np.pi*np.cumsum(f)/SR))*np.exp(-t/.55)
kick=np.sin(2*np.pi*np.cumsum(55+260*np.exp(-t/.012))/SR)*np.exp(-t/.09)
cl=rng.standard_normal(n); cl=np.diff(np.concatenate([[0],cl]))*np.exp(-t/.05)*(t<.25)
sw=rng.standard_normal(n); out=np.zeros(n); lo=0.0
for i in range(n):
    a=0.02+0.25*np.exp(-t[i]/.25); lo+=a*(sw[i]-lo); out[i]=lo
sweep=out*np.exp(-t/.35)
save(OUT+'/phonk.wav',0.9*b808+0.7*kick+0.35*cl+0.6*sweep)
# coin: soft warm coin/chime for the slip (replaces the harsh ka-ching)
n=int(SR*.9); t=np.arange(n)/SR; x=np.zeros(n)
for st,fq in [(0,1175),(.08,1568)]:
    i=int(st*SR); tt=t[:n-i]; x[i:]+=(np.sin(2*np.pi*fq*tt)+.25*np.sin(2*np.pi*2*fq*tt))*np.exp(-tt/.22)*np.minimum(tt/.004,1)
k=int(SR*.03); x[:k]+=0.3*np.sin(2*np.pi*180*t[:k])*np.exp(-t[:k]/.01)
save(OUT+'/coin.wav',x)
