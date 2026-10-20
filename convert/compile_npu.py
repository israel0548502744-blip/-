"""Ahead-of-time compile a TripoSR ONNX model for the Snapdragon NPU (Hexagon HTP).

Compiling the 1.6 GB encoder on the phone takes many minutes and gigabytes of RAM (the app
was killed while doing it). Instead, compile it here on an x86_64 Linux machine with the
onnxruntime-qnn plugin; the phone then only loads the finished QNN context binary.

The output is an "EPContext" ONNX model plus a .bin file; both must stay together.

  python compile_npu.py model.onnx out_dir --htp-arch 79 --soc-model 69
"""

import argparse
import os
import time

import onnxruntime as ort
import onnxruntime_qnn


def main():
    p = argparse.ArgumentParser()
    p.add_argument("model")
    p.add_argument("out_dir")
    p.add_argument("--htp-arch", default="79", help="Hexagon arch: 73=8 Gen 2, 75=8 Gen 3, 79=8 Elite, 81=8 Elite Gen 5")
    p.add_argument("--soc-model", default="69", help="QNN SoC id (69 = SM8750, Snapdragon 8 Elite)")
    p.add_argument("--suffix", default="")
    args = p.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)
    base = os.path.splitext(os.path.basename(args.model))[0]
    out = os.path.join(args.out_dir, f"{base}{args.suffix}_ctx.onnx")

    ort.register_execution_provider_library(onnxruntime_qnn.get_ep_name(), onnxruntime_qnn.get_library_path())
    devices = [d for d in ort.get_ep_devices() if d.ep_name == onnxruntime_qnn.get_ep_name()]
    print("QNN EP devices:", [(d.device.type, d.ep_metadata) for d in devices], flush=True)

    so = ort.SessionOptions()
    so.add_session_config_entry("ep.context_enable", "1")
    so.add_session_config_entry("ep.context_embed_mode", "0")
    so.add_session_config_entry("ep.context_file_path", out)
    # Fail instead of silently putting unsupported ops on the CPU: everything must run on the NPU.
    so.add_session_config_entry("session.disable_cpu_ep_fallback", "1")
    options = {
        "backend_path": onnxruntime_qnn.get_qnn_htp_path(),
        "htp_arch": args.htp_arch,
        "soc_model": args.soc_model,
        "enable_htp_fp16_precision": "1",
        "htp_graph_finalization_optimization_mode": "3",
    }
    so.add_provider_for_devices(devices, options)

    t0 = time.time()
    ort.InferenceSession(args.model, sess_options=so)
    print(f"compiled {args.model} -> {out} in {time.time() - t0:.1f}s", flush=True)
    for f in sorted(os.listdir(args.out_dir)):
        print(f"  {f}: {os.path.getsize(os.path.join(args.out_dir, f)) / 2**20:.1f} MB")


if __name__ == "__main__":
    main()
