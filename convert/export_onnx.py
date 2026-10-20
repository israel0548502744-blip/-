"""Export TripoSR to ONNX models that run on Android (ONNX Runtime + QNN / Hexagon NPU).

TripoSR is split into two graphs:

  triposr_encoder.onnx   image [1,3,512,512] (RGB, 0..1, background already grey)
                         -> scene_codes [3,40,64,64]  (the triplane)
                         DINO ViT-B/16 + 16-layer triplane transformer. This is the
                         heavy part and is what runs on the NPU.

  triposr_decoder.onnx   scene_codes [3,40,64,64] + points [CHUNK,3] in (-0.87, 0.87)
                         -> density_logit [CHUNK,1], color [CHUNK,3]
                         Triplane grid-sample + small NeRF MLP. The app calls it in
                         fixed-size chunks over a 3D grid, then runs marching cubes.

density_logit is the value *before* exp(), so the app compares it against
ln(threshold) instead of exp()-ing it. This avoids fp16 overflow on the NPU.

Usage:
  python export_onnx.py --out ../android/app/src/main/assets          # real weights (HF)
  python export_onnx.py --out /tmp/test --random-weights --verify     # offline smoke test
"""

import argparse
import json
import math
import os
import sys
import tempfile
import types

import numpy as np
import torch
import torch.nn as nn

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "third_party", "TripoSR"))

# tsr imports these at module level but they are only needed for the desktop
# pipeline (background removal, video, texture baking, marching cubes).
for _name in ("rembg", "imageio", "xatlas", "moderngl", "torchmcubes"):
    if _name not in sys.modules:
        try:
            __import__(_name)
        except ImportError:
            _stub = types.ModuleType(_name)
            _stub.marching_cubes = None
            sys.modules[_name] = _stub

DECODER_CHUNK = 32768

# Same content as stabilityai/TripoSR config.yaml; only used with --random-weights.
OFFLINE_CONFIG = {
    "cond_image_size": 512,
    "image_tokenizer_cls": "tsr.models.tokenizers.image.DINOSingleImageTokenizer",
    "image_tokenizer": {"pretrained_model_name_or_path": "facebook/dino-vitb16"},
    "tokenizer_cls": "tsr.models.tokenizers.triplane.Triplane1DTokenizer",
    "tokenizer": {"plane_size": 32, "num_channels": 1024},
    "backbone_cls": "tsr.models.transformer.transformer_1d.Transformer1D",
    "backbone": {
        "in_channels": 1024,
        "num_attention_heads": 16,
        "attention_head_dim": 64,
        "num_layers": 16,
        "cross_attention_dim": 768,
    },
    "post_processor_cls": "tsr.models.network_utils.TriplaneUpsampleNetwork",
    "post_processor": {"in_channels": 1024, "out_channels": 40},
    "decoder_cls": "tsr.models.network_utils.NeRFMLP",
    "decoder": {
        "in_channels": 120,
        "n_neurons": 64,
        "n_hidden_layers": 9,
        "activation": "silu",
    },
    "renderer_cls": "tsr.models.nerf_renderer.TriplaneNeRFRenderer",
    "renderer": {
        "radius": 0.87,
        "feature_reduction": "concat",
        "density_activation": "exp",
        "density_bias": -1.0,
        "num_samples_per_ray": 128,
    },
}


class Encoder(nn.Module):
    """TSR.forward without the PIL preprocessing."""

    def __init__(self, tsr):
        super().__init__()
        self.tsr = tsr

    def forward(self, image):
        image_tokens = self.tsr.image_tokenizer(image[:, None])  # B 1 C Nt
        image_tokens = image_tokens[:, 0].permute(0, 2, 1)  # B Nt C
        tokens = self.tsr.tokenizer(image.shape[0])
        tokens = self.tsr.backbone(tokens, encoder_hidden_states=image_tokens)
        scene_codes = self.tsr.post_processor(self.tsr.tokenizer.detokenize(tokens))
        return scene_codes[0]


class Decoder(nn.Module):
    """TriplaneNeRFRenderer.query_triplane, returning density before activation."""

    def __init__(self, tsr):
        super().__init__()
        self.decoder = tsr.decoder
        self.radius = tsr.renderer.cfg.radius
        self.density_bias = tsr.renderer.cfg.density_bias
        assert tsr.renderer.cfg.feature_reduction == "concat"

    def forward(self, scene_codes, points):
        x = points / self.radius
        indices2d = torch.stack((x[:, [0, 1]], x[:, [0, 2]], x[:, [1, 2]]), dim=0)
        feats = torch.nn.functional.grid_sample(
            scene_codes, indices2d[:, None], align_corners=False, mode="bilinear"
        )  # 3 C 1 N
        n_planes, channels, _, n = feats.shape
        feats = feats[:, :, 0, :].permute(2, 0, 1).reshape(n, n_planes * channels)
        out = self.decoder.layers(feats)
        return out[:, 0:1] + self.density_bias, torch.sigmoid(out[:, 1:4])


def load_model(model_dir, random_weights):
    if random_weights:
        import tsr.models.tokenizers.image as image_tok
        from omegaconf import OmegaConf
        from transformers import ViTConfig

        tmp = tempfile.mkdtemp()
        vit_cfg = os.path.join(tmp, "config.json")
        with open(vit_cfg, "w") as f:
            json.dump(ViTConfig().to_dict(), f)  # defaults == ViT-B/16
        image_tok.hf_hub_download = lambda **_: vit_cfg

        from tsr.system import TSR

        torch.manual_seed(0)
        return TSR(OmegaConf.create(OFFLINE_CONFIG))

    from tsr.system import TSR

    return TSR.from_pretrained(model_dir, config_name="config.yaml", weight_name="model.ckpt")


def bake_position_embeddings(model, size=512):
    """DINO was trained at 224px. transformers resizes its position embeddings to 512px on
    every call, which doesn't trace. The input size is fixed, so resize them once up front;
    ViT then sees matching sizes and uses them as is."""
    emb = model.image_tokenizer.model.embeddings
    patch = model.image_tokenizer.model.config.patch_size
    n = (size // patch) ** 2 + 1
    with torch.no_grad():
        dummy = torch.zeros(1, n, emb.position_embeddings.shape[-1])
        resized = emb.interpolate_pos_encoding(dummy, size, size)
    emb.position_embeddings = nn.Parameter(resized.clone())


def export(model, out_dir, opset):
    os.makedirs(out_dir, exist_ok=True)
    model.eval()
    bake_position_embeddings(model)
    encoder, decoder = Encoder(model).eval(), Decoder(model).eval()

    image = torch.rand(1, 3, 512, 512)
    enc_path = os.path.join(out_dir, "triposr_encoder.onnx")
    print("exporting encoder ->", enc_path, flush=True)
    with torch.no_grad():
        torch.onnx.export(
            encoder,
            (image,),
            enc_path,
            input_names=["image"],
            output_names=["scene_codes"],
            opset_version=opset,
            do_constant_folding=True,
            dynamo=False,
        )

    scene_codes = torch.randn(3, 40, 64, 64)
    points = (torch.rand(DECODER_CHUNK, 3) * 2 - 1) * 0.87
    dec_path = os.path.join(out_dir, "triposr_decoder.onnx")
    print("exporting decoder ->", dec_path, flush=True)
    with torch.no_grad():
        torch.onnx.export(
            decoder,
            (scene_codes, points),
            dec_path,
            input_names=["scene_codes", "points"],
            output_names=["density_logit", "color"],
            opset_version=opset,
            do_constant_folding=True,
            dynamo=False,
        )
    return encoder, decoder


def reference(model):
    """Run the untouched TripoSR forward pass, before export modifies the model."""
    torch.manual_seed(1)
    image = torch.rand(1, 3, 512, 512)
    with torch.no_grad():
        return image, model(image.permute(0, 2, 3, 1), device="cpu")[0]


def verify(model, encoder, decoder, out_dir, image, ref_codes):
    import onnxruntime as ort

    with torch.no_grad():
        enc_codes = encoder(image)
    print("encoder wrapper vs TSR.forward max diff:", (ref_codes - enc_codes).abs().max().item())

    sess = ort.InferenceSession(os.path.join(out_dir, "triposr_encoder.onnx"))
    onnx_codes = sess.run(None, {"image": image.numpy()})[0]
    print("encoder onnx vs torch max diff:", np.abs(onnx_codes - ref_codes.numpy()).max())

    points = (torch.rand(DECODER_CHUNK, 3) * 2 - 1) * 0.87
    with torch.no_grad():
        ref = model.renderer.query_triplane(model.decoder, points, ref_codes)
    sess = ort.InferenceSession(os.path.join(out_dir, "triposr_decoder.onnx"))
    logit, color = sess.run(None, {"scene_codes": ref_codes.numpy(), "points": points.numpy()})
    print("decoder density onnx vs torch max rel diff:",
          np.abs(np.exp(logit) - ref["density_act"].numpy()).max() / ref["density_act"].abs().max().item())
    print("decoder color onnx vs torch max diff:", np.abs(color - ref["color"].numpy()).max())


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model", default="stabilityai/TripoSR", help="HF repo id or local dir")
    p.add_argument("--out", required=True)
    p.add_argument("--opset", type=int, default=17)
    p.add_argument("--random-weights", action="store_true", help="offline test without downloading weights")
    p.add_argument("--verify", action="store_true", help="compare ONNX outputs with PyTorch")
    args = p.parse_args()

    model = load_model(args.model, args.random_weights)
    n_params = sum(p.numel() for p in model.parameters())
    print(f"TripoSR loaded: {n_params / 1e6:.1f}M parameters", flush=True)
    model.eval()
    ref = reference(model) if args.verify else None
    encoder, decoder = export(model, args.out, args.opset)
    if args.verify:
        verify(model, encoder, decoder, args.out, *ref)


if __name__ == "__main__":
    main()
