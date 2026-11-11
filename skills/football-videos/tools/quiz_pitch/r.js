const {chromium}=require('playwright');const fs=require('fs');(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1080,height:1920}});
await p.goto('file:///home/claude/fv/s1111/L/bg.html');await p.waitForTimeout(300);await p.screenshot({path:'/home/claude/fv/s1111/L/bg.png'});
await p.goto('file:///home/claude/fv/s1111/L/ov.html');await p.waitForTimeout(300);let pk=null,pf=null;const N=Math.round(12.15*30);
for(let f=0;f<N;f++){const k=await p.evaluate(t=>render(t),f/30);const fn=`/home/claude/fv/s1111/ov/o_${String(f).padStart(5,'0')}.png`;
 if(k===pk)fs.copyFileSync(pf,fn);else{await p.screenshot({path:fn,omitBackground:true});pk=k;pf=fn}}await b.close();})();
