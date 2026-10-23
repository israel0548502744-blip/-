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
