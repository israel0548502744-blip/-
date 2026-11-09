import cv2,sys,numpy as np
src,out=sys.argv[1],sys.argv[2]
K={0:(290,190,560,690),10:(225,140,545,690),16:(250,120,560,690),20:(255,100,576,640),26:(250,90,576,600),32:(250,90,576,600)}
ks=sorted(K)
def box(n):
  n=min(max(n,ks[0]),ks[-1])
  for a,b in zip(ks,ks[1:]):
    if a<=n<=b:
      f=(n-a)/(b-a); return [int(K[a][i]+(K[b][i]-K[a][i])*f) for i in range(4)]
cap=cv2.VideoCapture(src); W=int(cap.get(3)); H=int(cap.get(4)); fps=cap.get(5)
vw=cv2.VideoWriter(out,cv2.VideoWriter_fourcc(*'mp4v'),fps,(W,H)); n=0
while True:
  ok,f=cap.read()
  if not ok: break
  x0,y0,x1,y1=box(n); x0,y0=max(0,x0),max(0,y0); x1,y1=min(W,x1),min(H,y1)
  r=f[y0:y1,x0:x1]; B=24
  s=cv2.resize(r,(max(1,(x1-x0)//B),max(1,(y1-y0)//B)),interpolation=cv2.INTER_AREA)
  f[y0:y1,x0:x1]=cv2.resize(s,(x1-x0,y1-y0),interpolation=cv2.INTER_NEAREST)
  vw.write(f); n+=1
vw.release(); print('frames',n)
