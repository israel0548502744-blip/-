# Legal check (copyright, licenses, trademarks)

Status as of 2026-10-23. This is an engineering review, not legal advice. Before the app is
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
   or brands they don't own. That is their responsibility, but we need Terms of Use that say
   so, must not advertise it ("print your favourite Marvel hero"), and, if we ever host or sell
   user models, a takedown (DMCA) process.
4. **Privacy (low, required for Google Play).** Everything runs on the phone and no image is
   uploaded, which is good. Play still requires a privacy policy page; it must say that
   images stay on the device, crash reports are only shared when the user copies them, and
   what the model downloads contact (GitHub).
5. **QR Wi-Fi plates.** The password is inside the printed code; the app warns about it.

## Model outputs

TripoSR and TripoSG outputs (meshes) may be sold: MIT puts no restriction on outputs.
Lithophanes, reliefs, QR plates, keychains, cookie cutters and stamps are made by our own
code from the user's image or text.
