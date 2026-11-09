# AI upscale a clip with Real-ESRGAN (CPU): python3 upscale.py in.mp4 out.mp4 model.pth out_width
import sys,cv2,torch,numpy as np,time,subprocess
from spandrel import ModelLoader
src,out,mp,OW=sys.argv[1],sys.argv[2],sys.argv[3],int(sys.argv[4])
torch.set_num_threads(4)
m=ModelLoader().load_from_file(mp).eval()
cap=cv2.VideoCapture(src); fps=cap.get(5); frames=[]
while True:
  ok,f=cap.read()
  if not ok: break
  frames.append(f)
H,W=frames[0].shape[:2]; OH=int(round(H*OW/W/2))*2
p=subprocess.Popen(['ffmpeg','-v','error','-y','-f','rawvideo','-pix_fmt','bgr24','-s',f'{OW}x{OH}','-r',str(fps),'-i','-','-c:v','libx264','-preset','slow','-crf','8','-pix_fmt','yuv420p',out],stdin=subprocess.PIPE)
t0=time.time()
for i,f in enumerate(frames):
  x=torch.from_numpy(cv2.cvtColor(f,cv2.COLOR_BGR2RGB)).permute(2,0,1).float().div(255).unsqueeze(0)
  with torch.no_grad(): y=m(x).clamp(0,1)
  y=(y[0].permute(1,2,0).numpy()*255).round().astype(np.uint8)
  y=cv2.resize(cv2.cvtColor(y,cv2.COLOR_RGB2BGR),(OW,OH),interpolation=cv2.INTER_AREA)
  p.stdin.write(y.tobytes())
  if i==0: print('first frame',round(time.time()-t0,1),'s',flush=True)
p.stdin.close(); p.wait(); print('done',len(frames),'frames',round(time.time()-t0,1),'s')
