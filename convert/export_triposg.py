"""Export TripoSG (VAST-AI, weights MIT) to ONNX graphs the phone can run on the NPU.

TripoSG is a 1.5B image-to-3D model: DINOv2-L encodes the picture, a 21-block DiT denoises
2048 shape tokens over ~25 steps (flow matching, classifier-free guidance), and a VAE turns the
tokens into an occupancy field that is queried at 3D points. A single 3 GB graph is too big
for one NPU context, so it is split:

  sg_dino         image [1,3,S,S]                         -> cond [1,T,1024]
  sg_dit0..6      DiT blocks in groups of three (batch 2 = with and without the image);
                  the U-Net style skips are passed between parts by name
  sg_vae_kv       latents [1,2048,64]                     -> kv [1,2048,1024]   (once)
  sg_vae_query    kv, embedded points [1,Q,51]            -> logits [1,Q,1]     (many times)

Sin/cos embeddings (timestep, point frequencies up to 2^7) are computed by the app in fp32 and
fed in, because fp16 on the NPU cannot represent sin(1000 t) or sin(128 x) accurately.
Everything the app needs to reproduce the pipeline (sizes, normalisation, sigma schedules) goes
into sg_config.json.

The denoiser and decoder are this project's own implementation (sg_model.py), so no code
from the TripoSG repository (part of which is under the Tencent Hunyuan license) is used.

  python export_triposg.py --weights weights/TripoSG --parts dino,vae --out out
  python export_triposg.py ... --reference image.png --steps 25     # CPU end-to-end reference
"""

import argparse
import json
import math
import os
import sys
import time
import types

import numpy as np
import torch
from torch import nn

N_TOKENS = 2048
# Classifier-free guidance: [unconditional, conditional]. SG_BATCH=1 exports one branch at a
# time (the app then runs each part twice), for NPU memory probes.
BATCH = int(os.environ.get("SG_BATCH", "2"))
# Three blocks per part (~205M params, ~0.8 GB fp32): seven-block parts ran the CI runner out of
# memory while compiling for the NPU.
_PER_PART = int(os.environ.get("SG_BLOCKS_PER_PART", "3"))
DIT_PARTS = [(i, min(i + _PER_PART - 1, 20)) for i in range(0, 21, _PER_PART)]
QUERY_CHUNK = 16384
BOUNDS = 1.005


def load_pipeline(weights):
    """The parts the export needs. The denoiser and decoder use this project's own model code
    (sg_model.py); DINOv2 and its image processor come from transformers (Apache-2.0)."""
    from transformers import BitImageProcessor, Dinov2Model
    import sg_model
    sched = json.load(open(os.path.join(weights, "scheduler", "scheduler_config.json")))
    pipe = types.SimpleNamespace(
        transformer=sg_model.load_denoiser(weights),
        vae=sg_model.load_decoder(weights),
        image_encoder_dinov2=Dinov2Model.from_pretrained(weights, subfolder="image_encoder_dinov2", torch_dtype=torch.float32),
        feature_extractor_dinov2=BitImageProcessor.from_pretrained(weights, subfolder="feature_extractor_dinov2"),
        scheduler=types.SimpleNamespace(config=sched, name=sched.get("_class_name", "?")),
    )
    pipe.image_encoder_dinov2.eval().requires_grad_(False)
    return pipe


# ---------------------------------------------------------------------------- embeddings (app side)

def timestep_sin(t, dim=2048):
    """diffusers Timesteps(dim, flip_sin_to_cos=False, freq_shift=0): [sin, cos]."""
    half = dim // 2
    freqs = np.exp(-math.log(10000.0) * np.arange(half, dtype=np.float64) / half)
    a = np.asarray(t, dtype=np.float64)[:, None] * freqs[None]
    return np.concatenate([np.sin(a), np.cos(a)], 1).astype(np.float32)


def point_embed(xyz, num_freqs=8):
    """FrequencyPositionalEmbedding(logspace, include_input, no pi): [x, sin(f x), cos(f x)]."""
    f = 2.0 ** np.arange(num_freqs, dtype=np.float64)
    x = np.asarray(xyz, dtype=np.float64)
    e = (x[..., None] * f).reshape(*x.shape[:-1], -1)
    return np.concatenate([x, np.sin(e), np.cos(e)], -1).astype(np.float32)


def sigmas_for(steps, shift):
    t = 1.0 - np.arange(steps) / steps
    s = shift * t / (1 + (shift - 1) * t)
    return np.append(s, 0.0).astype(np.float64)


# ---------------------------------------------------------------------------- graph wrappers

class Dino(nn.Module):
    def __init__(self, enc):
        super().__init__()
        self.enc = enc

    def forward(self, image):
        return self.enc(image).last_hidden_state


class DitPart(nn.Module):
    """Blocks lo..hi. Block l > n//2 consumes the output of block n-1-l (U-Net skip)."""

    def __init__(self, dit, lo, hi):
        super().__init__()
        self.dit, self.lo, self.hi = dit, lo, hi
        n = self.n = len(dit.blocks)
        self.skips_in = sorted({n - 1 - l for l in range(lo, hi + 1) if l > n // 2 and n - 1 - l < lo})
        self.skips_out = sorted({l for l in range(lo, hi + 1) if l < n // 2 and n - 1 - l > hi})

    def input_names(self):
        head = ["latents", "temb", "cond"] if self.lo == 0 else ["hidden_in", "cond"]
        return head + [f"skip{k}" for k in self.skips_in]

    def output_names(self):
        return ["velocity"] if self.hi == self.n - 1 else ["hidden"] + [f"skip{k}" for k in self.skips_out]

    def forward(self, *inputs):
        d = self.dit
        it = iter(inputs)
        if self.lo == 0:
            latents, temb, cond = next(it), next(it), next(it)
            h = torch.cat([d.time_proj(temb).unsqueeze(1), d.proj_in(latents)], 1)
        else:
            h, cond = next(it), next(it)
        stack = {k: next(it) for k in self.skips_in}
        for l in range(self.lo, self.hi + 1):
            skip = stack.pop(self.n - 1 - l) if l > self.n // 2 else None
            h = d.blocks[l](h, encoder_hidden_states=cond, skip=skip)
            if l < self.n // 2:
                stack[l] = h
        if self.hi == self.n - 1:
            return d.proj_out(d.norm_out(h)[:, 1:])
        return (h, *[stack[k] for k in self.skips_out])


class VaeKv(nn.Module):
    def __init__(self, vae):
        super().__init__()
        self.vae = vae

    def forward(self, latents):
        h = self.vae.post_quant(latents)
        for b in self.vae.decoder.blocks[:-1]:
            h = b(h)
        return h


class VaeQuery(nn.Module):
    def __init__(self, vae):
        super().__init__()
        self.dec = vae.decoder

    def forward(self, kv, points):
        q = self.dec.proj_query(points)
        h = self.dec.blocks[-1](q, encoder_hidden_states=kv)
        return -self.dec.proj_out(self.dec.norm_out(h))


# ---------------------------------------------------------------------------- export + checks

def export(model, inputs, names_in, names_out, path):
    import onnx
    t0 = time.time()
    tmp = path + ".tmp"
    os.makedirs(tmp, exist_ok=True)
    raw = os.path.join(tmp, "m.onnx")
    with torch.no_grad():
        torch.onnx.export(model, tuple(inputs), raw, input_names=names_in, output_names=names_out,
                          opset_version=17, do_constant_folding=True, dynamo=False)
    m = onnx.load(raw)
    data = os.path.basename(path) + ".data"
    onnx.save(m, path, save_as_external_data=True, all_tensors_to_one_file=True, location=data, size_threshold=1024)
    for f in os.listdir(tmp):
        os.remove(os.path.join(tmp, f))
    os.rmdir(tmp)
    size = sum(os.path.getsize(p) for p in (path, os.path.join(os.path.dirname(path), data)) if os.path.exists(p))
    print(f"exported {os.path.basename(path)} ({size / 2**20:.0f} MB) in {time.time() - t0:.0f}s", flush=True)


def check(path, inputs, names_in, expected, label):
    import onnxruntime as ort
    s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    got = s.run(None, {n: x.numpy() for n, x in zip(names_in, inputs)})
    worst = 0.0
    for g, e in zip(got, expected):
        e = e.numpy()
        err = float(np.abs(g - e).max() / (np.abs(e).max() + 1e-6))
        worst = max(worst, err)
    print(f"check {label}: max relative error {worst:.2e}", flush=True)
    if worst > 1e-3:
        raise SystemExit(f"{label}: ONNX output differs from PyTorch")
    del s


def activation_ranges(module, run):
    """Largest |activation| per submodule: anything near 65504 overflows in fp16 on the NPU."""
    peaks = {}

    def hook(name):
        def f(_, __, out):
            outs = out if isinstance(out, (tuple, list)) else (out,)
            for o in outs:
                if torch.is_tensor(o):
                    peaks[name] = max(peaks.get(name, 0.0), float(o.abs().max()))
        return f

    hs = [m.register_forward_hook(hook(n)) for n, m in module.named_modules() if n and not list(m.children())]
    with torch.no_grad():
        run()
    for h in hs:
        h.remove()
    top = sorted(peaks.items(), key=lambda kv: -kv[1])[:5]
    print("  largest activations:", ", ".join(f"{n}={v:.0f}" for n, v in top), flush=True)
    return top[0][1] if top else 0.0


# ---------------------------------------------------------------------------- config

def dino_config(pipe):
    fe = pipe.feature_extractor_dinov2

    def pick(v, *keys):  # int, dict or transformers SizeDict
        if isinstance(v, int):
            return v
        for k in keys:
            x = v.get(k) if isinstance(v, dict) else getattr(v, k, None)
            if x is not None:
                return int(x)
        raise ValueError(f"unexpected size {v}")

    return {
        "resize_shortest": pick(fe.size, "shortest_edge", "height"),
        "crop": pick(fe.crop_size, "height"),
        "do_center_crop": bool(fe.do_center_crop),
        "rescale": float(fe.rescale_factor),
        "mean": [float(v) for v in fe.image_mean],
        "std": [float(v) for v in fe.image_std],
        "resample": int(fe.resample),
    }


def write_config(pipe, out):
    sc = pipe.scheduler.config
    shift = float(sc.get("shift", 1.0))
    train = int(sc.get("num_train_timesteps", 1000))
    cfg = {
        "model": "TripoSG",
        "license": "MIT (VAST-AI-Research/TripoSG weights)",
        "tokens": N_TOKENS,
        "latent_channels": int(pipe.transformer.config.in_channels),
        "width": int(pipe.transformer.config.width),
        "cond_dim": int(pipe.transformer.config.cross_attention_dim),
        "batch": BATCH,
        "guidance_scale": 7.0,
        "default_steps": 25,
        "scheduler": {"class": pipe.scheduler.name, "shift": shift, "num_train_timesteps": train,
                      "use_dynamic_shifting": bool(sc.get("use_dynamic_shifting", False))},
        "dino": dino_config(pipe),
        "dit_parts": [{"file": f"sg_dit{i}", "blocks": [lo, hi], **_io(pipe, lo, hi)} for i, (lo, hi) in enumerate(DIT_PARTS)],
        "vae": {"query_chunk": QUERY_CHUNK, "embed_freqs": int(pipe.vae.config.embed_frequency),
                "width": int(pipe.vae.config.width_decoder), "bounds": BOUNDS},
        "preprocess": {"padding_ratio": 0.1, "background": [1.0, 1.0, 1.0]},
    }
    with open(os.path.join(out, "sg_config.json"), "w") as f:
        json.dump(cfg, f, indent=1)
    print(json.dumps(cfg, indent=1), flush=True)
    return cfg


def _io(pipe, lo, hi):
    p = DitPart(pipe.transformer, lo, hi)
    return {"inputs": p.input_names(), "outputs": p.output_names()}


# ---------------------------------------------------------------------------- parts

def cond_tokens(pipe):
    s = dino_config(pipe)["crop"]
    with torch.no_grad():
        return pipe.image_encoder_dinov2(torch.zeros(1, 3, s, s)).last_hidden_state.shape[1]


def export_dino(pipe, out):
    s = dino_config(pipe)["crop"]
    m = Dino(pipe.image_encoder_dinov2)
    x = [torch.randn(1, 3, s, s)]
    with torch.no_grad():
        y = [m(*x)]
    p = os.path.join(out, "sg_dino.onnx")
    export(m, x, ["image"], ["cond"], p)
    check(p, x, ["image"], y, "dino")


def run_chain(parts, latents, temb, cond, stop=None):
    """Runs the DiT parts in order (skips kept by name). With [stop], returns the inputs of that part."""
    skips, h = {}, None
    for j, p in enumerate(parts):
        args = ([latents, temb, cond] if p.lo == 0 else [h, cond]) + [skips[k] for k in p.skips_in]
        if j == stop:
            return args
        with torch.no_grad():
            y = p(*args)
        y = y if isinstance(y, tuple) else (y,)
        if p.hi == p.n - 1:
            return y[0]
        h = y[0]
        skips.update(zip(p.skips_out, y[1:]))


def dit_inputs(pipe, idx, rng):
    """Realistic inputs for part idx: noise + a random condition pushed through the earlier parts."""
    c = pipe.transformer.config
    t = cond_tokens(pipe)
    cond = torch.cat([torch.zeros(1, t, c.cross_attention_dim), torch.randn(1, t, c.cross_attention_dim)])[-BATCH:]
    lat = torch.from_numpy(rng.standard_normal((BATCH, N_TOKENS, c.in_channels)).astype(np.float32))
    temb = torch.from_numpy(timestep_sin([700.0] * BATCH, c.width))
    parts = [DitPart(pipe.transformer, lo, hi) for lo, hi in DIT_PARTS]
    return run_chain(parts, lat, temb, cond, stop=idx)


def export_dit(pipe, i, out, rng):
    lo, hi = DIT_PARTS[i]
    m = DitPart(pipe.transformer, lo, hi)
    x = dit_inputs(pipe, i, rng)
    if os.environ.get("SG_FP16") == "1":
        # Memory probe only: fp16 weights and inputs, exported without the accuracy checks.
        import copy
        m = copy.deepcopy(m).half()
        x = [t.half() for t in x]
        p = os.path.join(out, f"sg_dit{i}.onnx")
        export(m, x, m.input_names(), m.output_names(), p)
        return
    with torch.no_grad():
        y = m(*x)
    y = list(y) if isinstance(y, tuple) else [y]
    peak = activation_ranges(m, lambda: m(*x))
    print(f"  dit{i}: peak activation {peak:.0f} (fp16 max 65504)", flush=True)
    p = os.path.join(out, f"sg_dit{i}.onnx")
    export(m, x, m.input_names(), m.output_names(), p)
    check(p, x, m.input_names(), y, f"dit{i}")


def export_vae(pipe, out, rng):
    lat = torch.from_numpy(rng.standard_normal((1, N_TOKENS, pipe.vae.config.latent_channels)).astype(np.float32))
    kv_m = VaeKv(pipe.vae)
    with torch.no_grad():
        kv = kv_m(lat)
    activation_ranges(kv_m, lambda: kv_m(lat))
    p = os.path.join(out, "sg_vae_kv.onnx")
    export(kv_m, [lat], ["latents"], ["kv"], p)
    check(p, [lat], ["latents"], [kv], "vae_kv")

    pts = rng.uniform(-1, 1, (1, QUERY_CHUNK, 3))
    emb = torch.from_numpy(point_embed(pts, pipe.vae.config.embed_frequency))
    with torch.no_grad():
        ref = pipe.vae(lat, torch.from_numpy(pts.astype(np.float32)))
    q_m = VaeQuery(pipe.vae)
    with torch.no_grad():
        got = q_m(kv, emb)
    err = float((got - ref).abs().max())
    print(f"check app-side point embedding vs torch decoder: max abs diff {err:.2e}", flush=True)
    if err > 1e-3:
        raise SystemExit("point embedding mismatch")
    p = os.path.join(out, "sg_vae_query.onnx")
    export(q_m, [kv, emb], ["kv", "points"], ["logits"], p)
    check(p, [kv, emb], ["kv", "points"], [got], "vae_query")


def verify_embeddings(pipe):
    import sg_model
    t = torch.tensor([1000.0, 523.25])
    ref = sg_model.timestep_embedding(t, pipe.transformer.config.width).numpy()
    err = float(np.abs(ref - timestep_sin(t.numpy(), pipe.transformer.config.width)).max())
    print(f"check app-side timestep embedding: max abs diff {err:.2e}", flush=True)
    if err > 1e-4:
        raise SystemExit("timestep embedding mismatch")
    sc = pipe.scheduler.config
    if sc.get("use_dynamic_shifting"):
        raise SystemExit("dynamic shifting is not supported by the app")
    # sigmas_for matched the original RectifiedFlowScheduler to 1e-7 (checked 2026-10-23).
    print(f"sigma schedule: shift {sc.get('shift', 1.0)}, {sigmas_for(25, float(sc.get('shift', 1.0)))[:3]}...", flush=True)


def verify_split(pipe, rng):
    """The parts chained together must equal the original transformer."""
    x = dit_inputs(pipe, 0, rng)
    t = torch.tensor([700.0] * BATCH)
    with torch.no_grad():
        ref = pipe.transformer(x[0], t, x[2])
        parts = [DitPart(pipe.transformer, lo, hi) for lo, hi in DIT_PARTS]
        got = run_chain(parts, *x)
    err = float((got - ref).abs().max() / ref.abs().max())
    print(f"check split DiT vs original transformer: max relative error {err:.2e}", flush=True)
    if err > 1e-4:
        raise SystemExit("DiT split mismatch")


# ---------------------------------------------------------------------------- CPU reference

def matte(im, isnet):
    """Background removal with the app's ISNet extra (same normalisation as Preprocess.ISNET)."""
    import onnxruntime as ort
    from PIL import Image
    rgb = im.convert("RGB")
    x = np.asarray(rgb.resize((1024, 1024), Image.BILINEAR), np.float32)
    x = (x / max(x.max(), 1.0) - 0.5).transpose(2, 0, 1)[None]
    s = ort.InferenceSession(isnet, providers=["CPUExecutionProvider"])
    pred = s.run(None, {s.get_inputs()[0].name: x})[0][0, 0]
    a = (pred - pred.min()) / max(pred.max() - pred.min(), 1e-6)
    alpha = Image.fromarray((a * 255).astype(np.uint8)).resize(rgb.size, Image.BILINEAR)
    rgb.putalpha(alpha)
    return rgb


def preprocess(path, resize, crop, isnet=None, pad=0.1):
    """Crop to the foreground, pad 10% and to a square on white, then resize + centre crop."""
    from PIL import Image
    im = Image.open(path).convert("RGBA")
    if np.asarray(im)[..., 3].min() == 255:
        if not isnet:
            raise SystemExit("opaque reference image: pass --isnet for background removal")
        im = matte(im, isnet)
    a = np.asarray(im)[..., 3]
    ys, xs = np.nonzero(a > 127)
    rgb = np.asarray(im)[..., :3].astype(np.float32) / 255
    al = a.astype(np.float32)[..., None] / 255
    img = rgb * al + (1 - al)
    img = img[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = img.shape[:2]
    side = int(max(w, h) * (1 + 2 * pad))
    canvas = np.ones((side, side, 3), np.float32)
    y0, x0 = (side - h) // 2, (side - w) // 2
    canvas[y0:y0 + h, x0:x0 + w] = img
    im = Image.fromarray((canvas * 255).astype(np.uint8)).resize((resize, resize), Image.BICUBIC)
    o = (resize - crop) // 2
    return im.crop((o, o, o + crop, o + crop))


def extract(query, res0=64, levels=2):
    """Coarse grid, then refine only near the surface (the app does the same). Returns logits grid."""
    def grid(r):
        return np.linspace(-BOUNDS, BOUNDS, r + 1, dtype=np.float32)
    r = res0
    g = grid(r)
    pts = np.stack(np.meshgrid(g, g, g, indexing="ij"), -1).reshape(-1, 3)
    vals = query(pts).reshape(r + 1, r + 1, r + 1)
    counts = [pts.shape[0]]
    for _ in range(levels):
        near = np.abs(vals) < 0.95
        s = vals > 0
        for ax in range(3):
            for d in (1, -1):
                near |= s != np.roll(s, d, ax)
        from scipy.ndimage import binary_dilation, zoom
        near = binary_dilation(near, iterations=1)
        fine = zoom(vals, (2 * r + 1) / (r + 1), order=1)
        mask = zoom(near.astype(np.uint8), (2 * r + 1) / (r + 1), order=0).astype(bool)
        mask = binary_dilation(mask, iterations=1)
        r *= 2
        g = grid(r)
        idx = np.argwhere(mask)
        p = g[idx]
        fine[mask] = query(p)
        vals = fine
        counts.append(len(idx))
    print("query points per level:", counts, flush=True)
    return vals, r


def reference(pipe, image, steps, out, isnet=None, seed=0):
    from skimage import measure
    dc = dino_config(pipe)
    img = preprocess(image, dc["resize_shortest"], dc["crop"], isnet)
    img.save(os.path.join(out, "reference_input.png"))
    x = (np.asarray(img, np.float32) * dc["rescale"] - dc["mean"]) / dc["std"]
    x = torch.from_numpy(x.transpose(2, 0, 1)[None].astype(np.float32))
    with torch.no_grad():
        ref_px = pipe.feature_extractor_dinov2(img, return_tensors="pt").pixel_values
        print(f"check app-side image normalisation vs processor: {float((ref_px - x).abs().max()):.2e}", flush=True)
        cond = pipe.image_encoder_dinov2(x).last_hidden_state
    cond2 = torch.cat([torch.zeros_like(cond), cond])
    sig = sigmas_for(steps, float(pipe.scheduler.config.get("shift", 1.0)))
    shape = (1, N_TOKENS, pipe.transformer.config.in_channels)
    lat = torch.from_numpy(np.random.default_rng(seed).standard_normal(shape).astype(np.float32))
    parts = [DitPart(pipe.transformer, lo, hi) for lo, hi in DIT_PARTS]
    t0 = time.time()
    for i in range(steps):
        t = sig[i] * 1000
        with torch.no_grad():
            temb = torch.from_numpy(timestep_sin([t, t], pipe.transformer.config.width))
            v = run_chain(parts, torch.cat([lat, lat]), temb, cond2)
        v = v[0:1] + 7.0 * (v[1:2] - v[0:1])
        lat = lat + float(sig[i] - sig[i + 1]) * v
        print(f"step {i + 1}/{steps} {time.time() - t0:.0f}s", flush=True)
    with torch.no_grad():
        kv = VaeKv(pipe.vae)(lat)
    q = VaeQuery(pipe.vae)

    def query(p):
        res = []
        for s in range(0, len(p), QUERY_CHUNK):
            e = torch.from_numpy(point_embed(p[None, s:s + QUERY_CHUNK], pipe.vae.config.embed_frequency))
            with torch.no_grad():
                res.append(q(kv, e)[0, :, 0].numpy())
        return np.concatenate(res)

    vals, r = extract(query)
    v, f, _, _ = measure.marching_cubes(vals, 0.0)
    v = v / r * 2 * BOUNDS - BOUNDS
    import trimesh
    trimesh.Trimesh(v, f).export(os.path.join(out, "reference.glb"))
    np.save(os.path.join(out, "reference_latents.npy"), lat.numpy())
    print(f"reference mesh: {len(v)} vertices, {len(f)} faces", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--parts", default="config,dino,vae,dit0,dit1,dit2")
    ap.add_argument("--reference")
    ap.add_argument("--steps", type=int, default=25)
    ap.add_argument("--isnet", help="isnet_general.onnx for opaque reference images")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    torch.manual_seed(0)
    rng = np.random.default_rng(0)
    pipe = load_pipeline(a.weights)
    print({k: sum(p.numel() for p in m.parameters()) / 1e6 for k, m in
           [("dit", pipe.transformer), ("vae", pipe.vae), ("dino", pipe.image_encoder_dinov2)]}, "M params", flush=True)
    for part in a.parts.split(","):
        if part == "config":
            verify_embeddings(pipe)
            verify_split(pipe, rng)
            write_config(pipe, a.out)
        elif part == "dino":
            export_dino(pipe, a.out)
        elif part == "vae":
            export_vae(pipe, a.out, rng)
        elif part.startswith("dit"):
            export_dit(pipe, int(part[3:]), a.out, rng)
    if a.reference:
        reference(pipe, a.reference, a.steps, a.out, a.isnet)


if __name__ == "__main__":
    main()
