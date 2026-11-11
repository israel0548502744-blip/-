import cv2,sys,json
src,out,S,key=sys.argv[1],sys.argv[2],float(sys.argv[3]),sys.argv[4]
KS={'A':{0:(0,100,280,540),6:(10,120,280,540),12:(0,120,240,560),18:(0,380,190,576),21:(0,380,190,576)},
    'B':{0:(185,410,260,565),6:(195,400,260,545),12:(195,380,280,540),18:(180,370,300,505),24:(195,380,300,520),30:(190,385,278,530),36:(140,445,265,576),45:(165,490,262,576)},
    'C':{0:(0,290,120,510),6:(0,290,140,510),12:(0,280,190,500),18:(0,285,110,500),24:(0,285,95,510),30:(0,300,70,530),36:(0,230,90,480),40:(0,230,90,480)}}
K=KS[key]; ks=sorted(K)
def box(n):
  if n>ks[-1]: return None
  for a,b in zip(ks,ks[1:]):
    if a<=n<=b:
      f=(n-a)/(b-a); return [int((K[a][i]+(K[b][i]-K[a][i])*f)*S) for i in range(4)]
cap=cv2.VideoCapture(src); W=int(cap.get(3)); H=int(cap.get(4)); fps=cap.get(5)
vw=cv2.VideoWriter(out,cv2.VideoWriter_fourcc(*'mp4v'),fps,(W,H)); n=0
while True:
  ok,f=cap.read()
  if not ok: break
  b=box(n)
  if b:
    x0,y0,x1,y1=max(0,b[0]),max(0,b[1]),min(W,b[2]),min(H,b[3]); r=f[y0:y1,x0:x1]; B=int(22*S)
    s=cv2.resize(r,(max(1,(x1-x0)//B),max(1,(y1-y0)//B)),interpolation=cv2.INTER_AREA)
    f[y0:y1,x0:x1]=cv2.resize(s,(x1-x0,y1-y0),interpolation=cv2.INTER_NEAREST)
  vw.write(f); n+=1
vw.release(); print(key,'frames',n)
