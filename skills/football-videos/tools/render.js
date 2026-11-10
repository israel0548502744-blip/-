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
  const imgs = [...new Set(JSON.parse(fs.readFileSync(tlPath, 'utf8')).flatMap(b => [b.m, b.s, b.k].filter(Boolean)).concat(['logo.png']))];
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
