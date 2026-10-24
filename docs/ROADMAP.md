# Roadmap

Decisions and requirements to carry through the project. Newest first.

## Business requirement (owner, 2026-10-23) — do later, design for it now
The app should be able to reach ~100,000 users with all generation running on their own phones
(no server cost per model), while users still depend on the owner's service and it is hard to
copy, so the owner can earn from it. Points to design for:
- Model packs downloaded from a server the owner controls (not public GitHub releases), behind
  an account / license check, with signed and expiring download URLs.
- Entitlements (free tier vs paid: models, quality levels, exports, print-ready 3MF) checked by
  the owner's backend; the app caches them with a short grace period offline.
- Google Play Billing for subscriptions / credits; Play Integrity API so modified or re-signed
  APKs and emulators don't get entitlements or model downloads.
- Model files encrypted at rest with per-device keys from the Android Keystore; decrypted only
  in memory when loading (raises the bar, can't make extraction impossible).
- Private signing key in a secret store (not the repo keystore used for test builds), R8/obfuscation,
  no secrets in the APK.
- Value that stays server-side even with on-device inference: accounts, sync/backup of
  projects, community gallery/marketplace of printable models, print-service partners, updates
  and new model packs.
- Keep license obligations for bundled models (attribution file / about screen).

## Model packs (owner, 2026-10-23)
Downloadable, selectable generators in addition to TripoSR: TripoSG (MIT), PartCrafter (MIT,
multi-part, built on TripoSG), Step1X-3D geometry (Apache-2.0). Start with an on-device
feasibility experiment for TripoSG (convert in CI, measure, test on the S25 Ultra) before
building UI for all three.

## Print templates — done and next (2026-10-23)
Done: lithophane (flat and lamp cylinder), relief, QR (link / Wi-Fi / WhatsApp / phone / text,
optional caption), name keychain (outline / plate / round pet tag, key ring), cookie cutter,
stamp. All real-size, watertight, sliced in PrusaSlicer.
Next ideas, in order of value/effort:
- Laser / vinyl cutter export (SVG, DXF) of the 2D masks: QR, names, stencils, cookie outlines
  for Cricut and laser cutters — same masks, a second market.
- Stencil with automatic bridges (letters like ם, O, A keep their inner islands).
- Photo medallion / coin: relief inside a circle with a key ring.
- Braille labels (Hebrew + English), ADA dot sizes. Needs a verified Hebrew Braille table.
- Two-colour "pause at height" note in the 3MF (PrusaSlicer/Bambu custom G-code) so the colour
  change is automatic.
- Map / terrain tiles from elevation data (needs an open DEM source, e.g. Copernicus 30 m).
- Phone stand and cable organiser with a name, from parametric shapes.

## Model research (2026-10-23)
- TRELLIS.2 (Microsoft, MIT, 4B, textured PBR output) — best open quality, but 24 GB GPU class;
  too big for the phone today, candidate for a later "quality" pack once TripoSG is proven.
- Pixal3D (Tencent ARC, MIT) — pixel-aligned image-to-3D; check its NOTICE file (third-party
  parts) and DINOv3 dependency (gated licence) before use.
- InstantMesh (Apache-2.0 code) — depends on Zero123++/Stable Diffusion weights with their own
  licences; not clean.
- UniTEX, EmbodiedGen texture (Apache-2.0) — texture generation for an existing mesh: the
  missing piece for TripoSG (geometry only). Evaluate size on device.
- Text-to-3D: no small clean model found; FLUX.2 klein 4B (Apache-2.0) text-to-image + TripoSG
  remains the plan.

## App structure and next UX steps (2026-10-24)

Done: bottom bar Home · Gallery · + · Models · Settings; the + opens a "what do we make?" sheet
(photo to 3D or one of six templates); reports live in Settings with an error badge; card
pickers instead of segmented bars; startup self-check feeding the reports; terms accepted on
first launch.

Next ideas, in order of value:
1. **Color for TripoSG from TripoSR's color field** (no new model or license, see LEGAL.md).
2. **Onboarding (3 swipe cards)** on first launch: what the app does, best photo tips,
   download the model. Replaces the bare terms dialog.
3. **Gallery filters and search:** by kind (3D / keychain / QR …), sort by date or size,
   long-press multi-select for delete/share.
4. **"Print again" presets:** remember the last size/thickness per template.
5. **Haptics and sound** on finish; a real app icon and splash screen with the new name.
6. **Light theme** option (some users print outdoors / in bright rooms).
7. **Accessibility:** content descriptions on all icons, TalkBack check, font-scale 1.3 check.
8. **Play build flavor** without the self-updater (see PLAY_RELEASE.md).

## App name candidates (2026-10-24)

Rejected: "TripoSR 3D" (Tripo is VAST's brand), "3D Creator" (Sony's scanner app, 10M+ installs),
"3DCraftor" (too close to the existing 3D modeller "3DCrafter"). Candidates still to clear
with a trademark search: **Printora**, **Snap2Print**, **Formio**, **Pixel2Part**, **Makeo 3D**.
