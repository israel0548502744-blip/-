# Permission e-mails (send from israel0546631352@gmail.com)

Send in English. Where to find the address is under each one. Keep every answer: a written
"yes" is the proof you need later (store review, lawyer, investors).

---

## 1. VAST-AI-Research — TripoSG weights (most important)

**Where:** the e-mail addresses on the TripoSG paper (arxiv.org/abs/2502.06608, first page), or open
an Issue at github.com/VAST-AI-Research/TripoSG. The company site is tripo3d.ai (contact page).

**Subject:** Commercial use of TripoSG weights in an on-device mobile app

Hello,

I am an independent developer in Israel building "Makeo 3D", an Android app that turns a photo into
a 3D-printable model entirely on the phone (no server). I would like to offer TripoSG as an optional
download inside the app, and later sell the app (one-time purchase or subscription).

What I do:
- I use the TripoSG weights published on Hugging Face under the MIT license, unchanged.
- I do not use your repository's model code: I wrote my own implementation that loads the weights.
- I convert the weights to the Qualcomm NPU format and host the converted files for download.
- The app shows the MIT notice and credits VAST-AI-Research.

Could you please confirm that:
1. this commercial use of the TripoSG weights is allowed under the MIT license, and
2. the weights carry no additional obligations from the Tencent Hunyuan Community License
   (which applies to parts of the original code)?

Thank you very much for publishing TripoSG.

Best regards,
Israel
Makeo 3D — israel0546631352@gmail.com

---

## 2. Tencent Hunyuan — confirm we need no license

**Where:** the contact address in the LICENSE / Notice file of github.com/Tencent-Hunyuan/Hunyuan3D-2
(the license names a contact for licensing questions), or the Issues page there.

**Subject:** Question about the Hunyuan Community License and TripoSG weights

Hello,

I develop "Makeo 3D", an Android app that creates 3D models on the phone. I plan to use the TripoSG
model weights by VAST-AI-Research, which are published under the MIT license. Parts of TripoSG's
original code are based on Hunyuan3D and carry the Tencent Hunyuan Community License.

I do not use any Tencent code: I wrote my own implementation, and only load VAST's MIT weights. The
app will be sold worldwide, possibly including the EU, UK and South Korea.

Could you please confirm that this use needs no license from Tencent, or tell me what is required?

Best regards,
Israel — israel0546631352@gmail.com

---

## 3. Qualcomm — shipping the QNN runtime inside the app

**Where:** Qualcomm AI Hub / developer support (aihub.qualcomm.com → Contact or Slack community),
or qualcomm.com → Contact us → Developer.

**Subject:** Redistributing QNN HTP runtime libraries in a commercial Android app

Hello,

I develop "Makeo 3D", a paid Android app that runs AI models on the Hexagon NPU through ONNX Runtime
with the QNN execution provider. The app includes the QNN runtime libraries (libQnnHtp.so,
libQnnHtpV79Skel.so, libQnnSystem.so, ...) from the onnxruntime-android-qnn package, as object
code only, and model files compiled with QAIRT in our build pipeline.

Could you please confirm that distributing these libraries inside a commercial app on Google Play
is permitted, and whether any notice or registration is required?

Best regards,
Israel — israel0546631352@gmail.com

---

## 4. PartCrafter — only if we add it later

**Where:** the authors' e-mails on the PartCrafter paper (arXiv), or Issues on its GitHub page.

**Subject:** Commercial use of PartCrafter weights

Hello,

I develop "Makeo 3D", an Android app that creates 3D-printable models on the phone. I am considering
offering PartCrafter as an optional model. Could you tell me under which license the released
weights are published, and whether commercial use in a paid app is allowed (the weights appear to
be trained from TripoSG)?

Best regards,
Israel — israel0546631352@gmail.com
