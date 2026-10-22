"""Optional enhancement models for the app ("extras" packs), exported to ONNX and checked.

  realesrgan_x4v3.onnx      Real-ESRGAN general-x4v3 (BSD-3-Clause), photo upscaling / cleanup
  isnet_general.onnx        ISNet general-use (Apache-2.0), stronger background removal
  depth_anything_v2s.onnx   Depth Anything V2 Small (Apache-2.0; Base/Large are non-commercial)
  mobilesam_encoder.onnx    MobileSAM (Apache-2.0), tap-to-select: image encoder
  mobilesam_decoder.onnx    MobileSAM prompt/mask decoder

Every model is run once with onnxruntime after export and its license is printed from the
source so the CI log documents it.
"""
import argparse, json, os, urllib.request
import numpy as np
import onnxruntime as ort
import torch
import torch.nn as nn


def fetch(url, path):
    if not os.path.exists(path):
        print("download", url)
        urllib.request.urlretrieve(url, path)
    return path


def show_license(name, url):
    try:
        text = urllib.request.urlopen(url, timeout=30).read().decode("utf-8", "replace")
        if url.startswith("https://huggingface.co/api/"):
            text = "HF cardData.license = " + str(json.loads(text).get("cardData", {}).get("license"))
        print(f"--- license {name} ({url}):\n" + "\n".join(text.strip().splitlines()[:3]))
    except Exception as e:  # informational only
        print(f"--- license {name}: could not fetch {url}: {e}")


def check(path, feeds):
    s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    outs = s.run(None, feeds)
    print(path, os.path.getsize(path) >> 20, "MB", [(o.shape, float(np.nanmin(o)), float(np.nanmax(o))) for o in outs])
    for o in outs:
        assert np.isfinite(o).all(), f"{path}: non-finite output"
    return outs


# ------------------------------------------------------------------ Real-ESRGAN general-x4v3
class SRVGGNetCompact(nn.Module):
    """Same layout as basicsr's SRVGGNetCompact (num_feat=64, num_conv=32, upscale=4, PReLU)."""
    def __init__(self, num_feat=64, num_conv=32, upscale=4):
        super().__init__()
        self.upscale = upscale
        body = [nn.Conv2d(3, num_feat, 3, 1, 1), nn.PReLU(num_parameters=num_feat)]
        for _ in range(num_conv):
            body += [nn.Conv2d(num_feat, num_feat, 3, 1, 1), nn.PReLU(num_parameters=num_feat)]
        body += [nn.Conv2d(num_feat, 3 * upscale * upscale, 3, 1, 1)]
        self.body = nn.ModuleList(body)
        self.upsampler = nn.PixelShuffle(upscale)

    def forward(self, x):
        out = x
        for m in self.body:
            out = m(out)
        out = self.upsampler(out)
        return out + nn.functional.interpolate(x, scale_factor=self.upscale, mode="nearest")


def realesrgan(out):
    w = fetch("https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth", "/tmp/realesr.pth")
    show_license("Real-ESRGAN", "https://raw.githubusercontent.com/xinntao/Real-ESRGAN/master/LICENSE")
    m = SRVGGNetCompact().eval()
    sd = torch.load(w, map_location="cpu")
    m.load_state_dict(sd.get("params", sd))
    p = f"{out}/realesrgan_x4v3.onnx"
    x = torch.rand(1, 3, 128, 128)
    torch.onnx.export(m, x, p, input_names=["image"], output_names=["upscaled"], opset_version=17,
                      dynamic_axes={"image": {2: "h", 3: "w"}, "upscaled": {2: "H", 3: "W"}})
    ref = m(x).detach().numpy()
    got = check(p, {"image": x.numpy()})[0]
    print("realesrgan max diff vs torch", float(np.abs(ref - got).max()))


# ------------------------------------------------------------------ ISNet (rembg build)
def isnet(out):
    p = fetch("https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx", f"{out}/isnet_general.onnx")
    show_license("DIS / ISNet", "https://raw.githubusercontent.com/xuebinqin/DIS/main/LICENSE.md")
    check(p, {ort.InferenceSession(p).get_inputs()[0].name: np.random.rand(1, 3, 1024, 1024).astype(np.float32)})


# ------------------------------------------------------------------ Depth Anything V2 Small
def depth(out):
    from transformers import AutoModelForDepthEstimation
    name = "depth-anything/Depth-Anything-V2-Small-hf"
    show_license("Depth Anything V2 Small", f"https://huggingface.co/api/models/{name}")
    show_license("Depth Anything V2 repo", "https://raw.githubusercontent.com/DepthAnything/Depth-Anything-V2/main/LICENSE")
    m = AutoModelForDepthEstimation.from_pretrained(name).eval()

    class Wrap(nn.Module):
        def __init__(self, m):
            super().__init__(); self.m = m
        def forward(self, x):
            return self.m(pixel_values=x).predicted_depth

    p = f"{out}/depth_anything_v2s.onnx"
    x = torch.rand(1, 3, 518, 518)
    torch.onnx.export(Wrap(m), x, p, input_names=["image"], output_names=["depth"], opset_version=17)
    ref = Wrap(m)(x).detach().numpy()
    got = check(p, {"image": x.numpy()})[0]
    print("depth max diff vs torch", float(np.abs(ref - got).max()))


# ------------------------------------------------------------------ MobileSAM
def mobilesam(out):
    from mobile_sam import sam_model_registry
    from mobile_sam.utils.onnx import SamOnnxModel
    w = fetch("https://raw.githubusercontent.com/ChaoningZhang/MobileSAM/master/weights/mobile_sam.pt", "/tmp/mobile_sam.pt")
    show_license("MobileSAM", "https://raw.githubusercontent.com/ChaoningZhang/MobileSAM/master/LICENSE")
    sam = sam_model_registry["vit_t"](checkpoint=w).eval()
    enc = f"{out}/mobilesam_encoder.onnx"
    x = torch.rand(1, 3, 1024, 1024)  # already resized (longest side 1024), normalized and padded
    torch.onnx.export(sam.image_encoder, x, enc, input_names=["image"], output_names=["embeddings"], opset_version=17)
    emb = check(enc, {"image": x.numpy()})[0]

    dec = f"{out}/mobilesam_decoder.onnx"
    model = SamOnnxModel(sam, return_single_mask=True)
    dummy = {
        "image_embeddings": torch.randn(1, 256, 64, 64),
        "point_coords": torch.randint(0, 1024, (1, 2, 2), dtype=torch.float),
        "point_labels": torch.tensor([[1, -1]], dtype=torch.float),
        "mask_input": torch.zeros(1, 1, 256, 256),
        "has_mask_input": torch.zeros(1),
        "orig_im_size": torch.tensor([1024, 1024], dtype=torch.float),
    }
    torch.onnx.export(model, tuple(dummy.values()), dec, input_names=list(dummy.keys()),
                      output_names=["masks", "iou_predictions", "low_res_masks"], opset_version=17,
                      dynamic_axes={"point_coords": {1: "n"}, "point_labels": {1: "n"}})
    feeds = {k: v.numpy() for k, v in dummy.items()}
    feeds["image_embeddings"] = emb
    check(dec, feeds)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="extras")
    ap.add_argument("--only", nargs="*")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for name, fn in [("realesrgan", realesrgan), ("isnet", isnet), ("depth", depth), ("mobilesam", mobilesam)]:
        if not a.only or name in a.only:
            print(f"=== {name}")
            fn(a.out)
