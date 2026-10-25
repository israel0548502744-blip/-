"""Quantize one TripoSG DiT part to int8 weights and 16-bit activations (QDQ ONNX for the QNN NPU).

Halving the weights halves the pack and lets more parts stay loaded on the NPU. Activations stay
16-bit, which keeps the precision the attention and the long skips need. The scales come from
real inputs of this part: a short sampling run on calibration pictures, with and without the
image (both guidance branches), at every step.

  python quantize_triposg.py --weights W --part 3 --images a.png,b.png --isnet isnet.onnx --out out

Writes out/sg_dit{part}.onnx (+ .data). sg_eval.py int8 checks the result against float.
"""
import argparse
import os
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import export_triposg as et  # noqa: E402
import sg_eval  # noqa: E402


def calibration_inputs(pipe, part, images, isnet, steps):
    """Inputs of DiT part [part] along short sampling runs, both guidance branches, as numpy feeds."""
    parts = [et.DitPart(pipe.transformer, lo, hi) for lo, hi in et.DIT_PARTS]
    names = parts[part].input_names()
    feeds = []
    c = pipe.transformer.config
    sig = et.sigmas_for(steps, float(pipe.scheduler.config.get("shift", 1.0)))
    for k, image in enumerate(images):
        cond, _ = sg_eval.encode(pipe, image, isnet)
        zeros = torch.zeros_like(cond)
        lat = torch.from_numpy(np.random.default_rng(k).standard_normal((1, et.N_TOKENS, c.in_channels)).astype(np.float32))
        for i in range(steps):
            temb = torch.from_numpy(et.timestep_sin([sig[i] * 1000], c.width))
            for cnd in (cond, zeros):
                args = et.run_chain(parts, lat, temb, cnd, stop=part)
                feeds.append({n: v.numpy().copy() for n, v in zip(names, args)})
            with torch.no_grad():
                vc = et.run_chain(parts, lat, temb, cond)
                vu = et.run_chain(parts, lat, temb, zeros)
            lat = lat + float(sig[i] - sig[i + 1]) * (vu + sg_eval.GUIDANCE * (vc - vu))
            print(f"  calibration image {k + 1}/{len(images)} step {i + 1}/{steps}", flush=True)
    return feeds


class Reader:
    def __init__(self, feeds):
        self.it = iter(feeds)

    def get_next(self):
        return next(self.it, None)


def quantize(float_model, feeds, out_path, per_channel):
    from onnxruntime.quantization import QuantType, quantize
    from onnxruntime.quantization.execution_providers.qnn import get_qnn_qdq_config, qnn_preprocess_model
    pre = float_model.replace(".onnx", ".pre.onnx")
    if qnn_preprocess_model(float_model, pre, save_as_external_data=True, all_tensors_to_one_file=True,
                            external_data_location=os.path.basename(pre) + ".data"):
        float_model = pre
    cfg = get_qnn_qdq_config(float_model, Reader(feeds), activation_type=QuantType.QUInt16,
                             weight_type=QuantType.QUInt8, per_channel=per_channel)
    cfg.use_external_data_format = True
    # Reduce ranges after every sample: one sample's attention maps alone take gigabytes.
    cfg.extra_options["CalibMaxIntermediateOutputs"] = 1
    quantize(float_model, out_path, cfg)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True)
    ap.add_argument("--part", type=int, required=True)
    ap.add_argument("--images", required=True, help="comma-separated calibration pictures")
    ap.add_argument("--isnet")
    ap.add_argument("--steps", type=int, default=8)
    ap.add_argument("--per-channel", type=int, default=1)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    if et.BATCH != 1:
        raise SystemExit("set SG_BATCH=1: the NPU parts run one guidance branch per call")
    os.makedirs(a.out, exist_ok=True)
    tmp = os.path.join(a.out, "float")
    os.makedirs(tmp, exist_ok=True)
    torch.manual_seed(0)
    pipe = et.load_pipeline(a.weights)
    feeds = calibration_inputs(pipe, a.part, a.images.split(","), a.isnet, a.steps)
    print(f"{len(feeds)} calibration samples", flush=True)
    lo, hi = et.DIT_PARTS[a.part]
    m = et.DitPart(pipe.transformer, lo, hi)
    x = [torch.from_numpy(feeds[0][n]) for n in m.input_names()]
    fp = os.path.join(tmp, f"sg_dit{a.part}.onnx")
    et.export(m, x, m.input_names(), m.output_names(), fp)
    del pipe
    out = os.path.join(a.out, f"sg_dit{a.part}.onnx")
    quantize(fp, feeds, out, bool(a.per_channel))
    for f in os.listdir(tmp):
        os.remove(os.path.join(tmp, f))
    os.rmdir(tmp)
    size = sum(os.path.getsize(os.path.join(a.out, f)) for f in os.listdir(a.out))
    print(f"quantized part {a.part}: {size / 2**20:.0f} MB", flush=True)


if __name__ == "__main__":
    main()
