# Legal check (copyright, licenses, trademarks)

Status as of 2026-10-24. This is an engineering review, not legal advice. Before the app is
sold, have a lawyer confirm the items marked **lawyer**.

## Everything the app ships or downloads

| Component | License | Commercial use | What we must do |
|---|---|---|---|
| TripoSR model | MIT (Tripo AI & Stability AI) | Yes | Keep the notice (done: Settings → open-source licenses) |
| TripoSG weights | MIT (VAST-AI-Research) | Yes | Notice (done) |
| TripoSG model *code* | Partly Tencent Hunyuan Community License | Not shipped: used only in CI to convert | **lawyer**: confirm converting with it does not bind the weights; the Hunyuan license excludes the EU, UK and South Korea |
| DINOv2 (inside TripoSG) | Apache-2.0 (Meta) | Yes | Notice + license text (done) |
| U-2-Net u2netp, ISNet | Apache-2.0 | Yes | Done |
| Real-ESRGAN x4v3 | BSD-3-Clause | Yes | Done |
| MobileSAM, Depth Anything V2 **Small** | Apache-2.0 | Yes | Done. Note: Depth Anything V2 Base/Large are CC-BY-NC, never use them |
| ONNX Runtime | MIT | Yes | Done |
| Qualcomm QNN runtime | Qualcomm AI Model & Software License | Yes, only as object code inside our app | Never offer the QNN libraries as a separate download; don't reverse engineer them; don't use for the listed prohibited uses (none apply to us) |
| ZXing, AndroidX, Compose, Kotlin | Apache-2.0 | Yes | Done |
| Rubik font (keychain/stamp text) | SIL OFL 1.1 | Yes, including printed products | Done. The phone's system font is no longer used for printed text (on Samsung it is Samsung's own font) |

Models we checked and rejected: RMBG (non-commercial), diso (CC BY-NC), pymeshlab (GPL),
Depth Anything V2 Base/Large (CC-BY-NC), FLUX.2 klein 9B (non-commercial), FLUX 3 Klein
(revenue cap), SF3D / SPAR3D (Stability community license, revenue cap).

## Risks found

1. **App name "TripoSR 3D" (high).** "Tripo" is the product brand of VAST (Tripo AI, Tripo
   Studio). Using it as our app's name invites a trademark complaint and Google Play rejects
   apps that look like another company's product. MIT lets us use the code, not the name.
   Rename the app and the package (`com.triposr.mobile`) before publishing; keep "uses
   TripoSR (MIT)" only in the credits.
2. **TripoSG / PartCrafter and Tencent Hunyuan (medium).** See the table. Until a lawyer says
   otherwise: don't sell the TripoSG pack in the EU, UK or South Korea, or replace the
   conversion code with our own implementation. Step1X-3D (Apache-2.0) has no such issue.
3. **What users create (medium).** Users can make 3D models or keychains of characters, logos
   or brands they don't own. **Done:** Terms of Use (`TERMS.md`, in the app and accepted on first
   launch) put that on the user and warn about food-contact prints. Still: never advertise it
   ("print your favourite Marvel hero"), and add a takedown process if we ever host user models.
4. **Privacy (low, required for Google Play).** **Done:** privacy policy (`PRIVACY.md`, Hebrew +
   English, also in the app). Missing: contact e-mail and a public URL (see `PLAY_RELEASE.md`).
5. **QR Wi-Fi plates.** The password is inside the printed code; the app warns about it.

## Model outputs

TripoSR and TripoSG outputs (meshes) may be sold: MIT puts no restriction on outputs.
Lithophanes, reliefs, QR plates, keychains, cookie cutters and stamps are made by our own
code from the user's image or text.

## Converting models in CI (question from 2026-10-24)

Converting TripoSG on the build server does not copy anyone's code into the app: the workflow
downloads the MIT weights, runs the model code once to trace the computation, and ships only the
resulting ONNX/QNN files (weights + graph). The open question is the same as risk 2: whether the
traced graph counts as a derivative of the Hunyuan-licensed model code. If the lawyer says it
does, the fix is to write our own model definition that loads the same weights, so the CI no
longer runs Tencent code. The conversion tools themselves (PyTorch BSD-3, ONNX Apache-2.0,
ONNX Runtime MIT, Qualcomm QAIRT under its license for compiling) allow this use.

## Candidate models checked on 2026-10-24

| Model | License | Depends on | Phone NPU? | Verdict |
|---|---|---|---|---|
| TRELLIS.2 (Microsoft, 4B) | MIT code and weights | DINOv3 image encoder (custom Meta license: commercial OK, no military/ITAR use, pass the terms on); nvdiffrast/nvdiffrec (NVIDIA, **non-commercial**) for rendering and PBR baking, which can be replaced; CuMesh/FlexGEMM sparse-voxel CUDA kernels | No: sparse 3D convolutions/attention have no QNN equivalent, 4B params ≈ 8 GB fp16, needs a 24 GB GPU | Best quality, not portable now. Revisit if a dense variant appears |
| Pixal3D (Tencent ARC) | Repo and model card say MIT; some secondary sources claim "academic only, not in the EU" | Built on TRELLIS.2 (same dependencies) and Direct3D-S2; read its NOTICE file | No, same sparse-voxel design, ~1.3B per stage | Wait for a clear license; ask the lawyer |
| UniTEX (texturing) | Code Apache-2.0 | LoRA on a FLUX model (FLUX.1-dev is **non-commercial**); optional super-resolution on SD3-Medium (Stability community license, revenue cap); its own checkpoints not released yet | Not as published (FLUX 12B) | Not usable commercially as is. Would need retraining on an Apache base such as FLUX.2 klein 4B |
| FLUX.2 klein 4B | Apache-2.0 | – | Plausible on the NPU, 4B int8 ≈ 4 GB | Usable for text-to-image → 3D later |

**Texture for TripoSG without new licenses:** color the TripoSG mesh by querying TripoSR's color
field (already on the phone) at the TripoSG vertices, after aligning the two coordinate frames.
This adds a few seconds and no new model.
