"""TripoSG shape model (diffusion transformer + occupancy decoder), written for this project.

Loads the published TripoSG weights (MIT, VAST-AI-Research) without the original repository's
model code. Only what the NPU export needs is here: the denoiser and the decoder half of the
VAE. The math follows the paper's architecture:

  denoiser   tokens = [time token, latents]; N blocks of self-attention, cross-attention to
             the image tokens and an MLP; long skips join block i with block N-1-i
  decoder    16 self-attention blocks over the latent set, then one cross-attention from
             frequency-embedded query points; the output is negated so positive = inside

Weight names match the checkpoint files so they load directly with load_state_dict.
"""
import json
import math
import os

import torch
import torch.nn.functional as F
from torch import nn


class Norm(nn.Module):
    """LayerNorm. The export runs in float32, which is what the checkpoint was trained with."""

    def __init__(self, dim, eps=1e-5, affine=True):
        super().__init__()
        self.eps = eps
        self.weight = nn.Parameter(torch.ones(dim)) if affine else None
        self.bias = nn.Parameter(torch.zeros(dim)) if affine else None

    def forward(self, x):
        return F.layer_norm(x, x.shape[-1:], self.weight, self.bias, self.eps)


class RmsNorm(nn.Module):
    def __init__(self, dim, eps=1e-6):
        super().__init__()
        self.eps = eps
        self.weight = nn.Parameter(torch.ones(dim))

    def forward(self, x):
        return x * torch.rsqrt(x.pow(2).mean(-1, keepdim=True) + self.eps) * self.weight


class Attention(nn.Module):
    """Multi-head attention. The checkpoint packs q/k/v per head (head-major), so after the
    projections the channels are regrouped head by head before splitting."""

    def __init__(self, dim, heads, context_dim=None, qk_norm=False, context_norm=False):
        super().__init__()
        self.heads = heads
        self.cross = context_dim is not None
        kv_dim = context_dim or dim
        self.to_q = nn.Linear(dim, dim, bias=False)
        self.to_k = nn.Linear(kv_dim, dim, bias=False)
        self.to_v = nn.Linear(kv_dim, dim, bias=False)
        self.to_out = nn.ModuleList([nn.Linear(dim, dim), nn.Identity()])
        self.norm_q = RmsNorm(dim // heads) if qk_norm else None
        self.norm_k = RmsNorm(dim // heads) if qk_norm else None
        self.norm_cross = nn.LayerNorm(kv_dim) if context_norm else None

    def forward(self, x, context=None):
        b, n, _ = x.shape
        h = self.heads
        if context is None:
            context = x
        elif self.norm_cross is not None:
            context = self.norm_cross(context)
        q, k, v = self.to_q(x), self.to_k(context), self.to_v(context)
        if self.cross:
            d = k.shape[-1] // h
            kv = torch.cat([k, v], -1).view(b, -1, h, 2 * d)
            k, v = kv[..., :d], kv[..., d:]
            q = q.view(b, n, h, d)
        else:
            d = q.shape[-1] // h
            qkv = torch.cat([q, k, v], -1).view(b, n, h, 3 * d)
            q, k, v = qkv[..., :d], qkv[..., d:2 * d], qkv[..., 2 * d:]
        q, k, v = (t.transpose(1, 2) for t in (q, k, v))
        if self.norm_q is not None:
            q, k = self.norm_q(q), self.norm_k(k)
        y = F.scaled_dot_product_attention(q, k, v)
        return self.to_out[0](y.transpose(1, 2).reshape(b, n, h * d))


class FeedForward(nn.Module):
    def __init__(self, dim, hidden):
        super().__init__()
        self.net = nn.ModuleList([nn.Module(), nn.Identity(), nn.Linear(hidden, dim)])
        self.net[0].proj = nn.Linear(dim, hidden)

    def forward(self, x):
        return self.net[2](F.gelu(self.net[0].proj(x)))


class Block(nn.Module):
    """Pre-norm transformer block: optional long-skip merge, self-attention, cross-attention, MLP."""

    def __init__(self, dim, heads, self_attn=True, context_dim=None, context_norm=False,
                 qk_norm=False, skip=False):
        super().__init__()
        if self_attn:
            self.norm1 = Norm(dim)
            self.attn1 = Attention(dim, heads, qk_norm=qk_norm)
        if context_dim is not None:
            self.norm2 = Norm(dim)
            self.attn2 = Attention(dim, heads, context_dim, qk_norm=qk_norm, context_norm=context_norm)
        self.norm3 = Norm(dim)
        self.ff = FeedForward(dim, dim * 4)
        if skip:
            self.skip_linear = nn.Linear(2 * dim, dim)
            self.skip_norm = Norm(dim)

    def forward(self, x, encoder_hidden_states=None, skip=None):
        if skip is not None:
            # The checkpoint was trained with the skip first and the norm after the projection.
            x = self.skip_norm(self.skip_linear(torch.cat([skip, x], -1)))
        if hasattr(self, "attn1"):
            x = x + self.attn1(self.norm1(x))
        if hasattr(self, "attn2"):
            x = x + self.attn2(self.norm2(x), encoder_hidden_states)
        return x + self.ff(self.norm3(x))


def timestep_embedding(t, dim):
    """Sinusoidal embedding, [sin, cos] halves, frequencies 10000^(-i/half)."""
    half = dim // 2
    freqs = torch.exp(-math.log(10000.0) * torch.arange(half, dtype=torch.float32) / half)
    a = t.float()[:, None] * freqs[None]
    return torch.cat([torch.sin(a), torch.cos(a)], -1)


class TimeMlp(nn.Module):
    def __init__(self, dim, hidden):
        super().__init__()
        self.linear_1 = nn.Linear(dim, hidden)
        self.linear_2 = nn.Linear(hidden, dim)

    def forward(self, x):
        return self.linear_2(F.gelu(self.linear_1(x)))


class Cfg(dict):
    __getattr__ = dict.get


class Denoiser(nn.Module):
    def __init__(self, cfg):
        super().__init__()
        self.config = cfg
        w, n = cfg.width, cfg.num_layers
        self.time_proj = TimeMlp(w, w * 4)
        self.proj_in = nn.Linear(cfg.in_channels, w)
        self.blocks = nn.ModuleList([
            Block(w, cfg.num_attention_heads, context_dim=cfg.cross_attention_dim, qk_norm=True, skip=l > n // 2)
            for l in range(n)])
        self.norm_out = nn.LayerNorm(w)
        self.proj_out = nn.Linear(w, cfg.in_channels)

    def forward(self, latents, t, cond):
        n = len(self.blocks)
        temb = self.time_proj(timestep_embedding(t, self.config.width))
        h = torch.cat([temb[:, None], self.proj_in(latents)], 1)
        skips = []
        for l, block in enumerate(self.blocks):
            h = block(h, cond, skips.pop() if l > n // 2 else None)
            if l < n // 2:
                skips.append(h)
        return self.proj_out(self.norm_out(h)[:, 1:])


def point_embedding(xyz, num_freqs):
    """[x, sin(2^k x), cos(2^k x)] for k < num_freqs, frequencies interleaved per coordinate."""
    f = 2.0 ** torch.arange(num_freqs, dtype=torch.float32)
    e = (xyz[..., None] * f).flatten(-2)
    return torch.cat([xyz, torch.sin(e), torch.cos(e)], -1)


class Decoder(nn.Module):
    def __init__(self, dim, heads, layers, query_dim):
        super().__init__()
        self.blocks = nn.ModuleList([Block(dim, heads) for _ in range(layers)] +
                                    [Block(dim, heads, self_attn=False, context_dim=dim, context_norm=True)])
        self.proj_query = nn.Linear(query_dim, dim)
        self.norm_out = nn.LayerNorm(dim)
        self.proj_out = nn.Linear(dim, 1)


class ShapeDecoder(nn.Module):
    def __init__(self, cfg):
        super().__init__()
        self.config = cfg
        qdim = 3 * (1 + 2 * cfg.embed_frequency)
        self.post_quant = nn.Linear(cfg.latent_channels, cfg.width_decoder)
        self.decoder = Decoder(cfg.width_decoder, cfg.num_attention_heads, cfg.num_layers_decoder, qdim)

    def kv(self, latents):
        h = self.post_quant(latents)
        for b in self.decoder.blocks[:-1]:
            h = b(h)
        return h

    def query(self, kv, embedded_points):
        d = self.decoder
        h = d.blocks[-1](d.proj_query(embedded_points), kv)
        return -d.proj_out(d.norm_out(h))

    def forward(self, latents, xyz):
        return self.query(self.kv(latents), point_embedding(xyz, self.config.embed_frequency))


DENOISER_DEFAULTS = dict(num_attention_heads=16, width=2048, in_channels=64, num_layers=21, cross_attention_dim=1024)
DECODER_DEFAULTS = dict(latent_channels=64, num_attention_heads=8, width_decoder=1024, num_layers_decoder=16,
                        embed_frequency=8, embed_include_pi=False)


def _state_dict(folder):
    from safetensors.torch import load_file
    for name in ("diffusion_pytorch_model.safetensors", "model.safetensors"):
        p = os.path.join(folder, name)
        if os.path.exists(p):
            return load_file(p)
    raise FileNotFoundError(f"no safetensors weights in {folder}")


def _config(folder, defaults):
    cfg = Cfg(defaults)
    cfg.update({k: v for k, v in json.load(open(os.path.join(folder, "config.json"))).items() if k in defaults})
    return cfg


def load_denoiser(weights):
    folder = os.path.join(weights, "transformer")
    m = Denoiser(_config(folder, DENOISER_DEFAULTS))
    sd = _state_dict(folder)
    unused = [k for k in sd if k.startswith(("blocks.", "time_proj.", "proj_in.", "norm_out.", "proj_out.")) is False]
    m.load_state_dict({k: v for k, v in sd.items() if k not in unused}, strict=True)
    if unused:
        print(f"denoiser: ignored {len(unused)} unused tensors, e.g. {unused[:3]}", flush=True)
    return m.float().eval().requires_grad_(False)


def load_decoder(weights):
    folder = os.path.join(weights, "vae")
    cfg = _config(folder, DECODER_DEFAULTS)
    if cfg.embed_include_pi:
        raise SystemExit("embed_include_pi=True is not supported")
    m = ShapeDecoder(cfg)
    sd = _state_dict(folder)
    keep = {k: v for k, v in sd.items() if k.startswith(("post_quant.", "decoder."))}
    m.load_state_dict(keep, strict=True)
    return m.float().eval().requires_grad_(False)


def shift_from_scheduler(weights):
    return float(json.load(open(os.path.join(weights, "scheduler", "scheduler_config.json"))).get("shift", 1.0))
