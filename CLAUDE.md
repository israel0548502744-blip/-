# Makeo 3D

Android app (Galaxy S25 Ultra, SM8750): photo → 3D model, fully on-device (ONNX Runtime + QNN NPU).
Package id is still `com.triposr.mobile`.

## Rules
- Start by reading `docs/HANDOFF.md` (current state, plan, lessons); update it after each milestone.
- At each milestone push a `milestone/<name>` tag and list it in HANDOFF section 7. Every build also has an `apk-N` tag.
- Answer the user in Hebrew.
- Only commercially permissive licenses (MIT/Apache/BSD/OFL). No non-commercial code or weights.
- Never claim something works without a real test (CI run, screenshot, or the user's device report).
- Work on branch `ccr-755aee2e-0wbwnj`; no PR unless asked. No model names in commits.
- After every app change: wait for CI "Build Makeo 3D Android APK" and send the user the APK link.

## Layout
- `android/` app (Compose UI in `ui/`, `Services.kt` = downloads, updater, `RemoteConfig`, `Region`).
- `convert/` model conversion (own TripoSG code in `sg_model.py`); `legal_pages.py` builds `docs/*.html`.
- `docs/` legal texts, `PLAY_RELEASE.md` (store checklist), `ROADMAP.md`, `LEGAL.md`.
- No local Gradle (offline): CI compiles. `ui-screenshots.yml` renders screens for review.

## Repositories
- This repo (`Makeo3D`) holds the code and goes private.
- `makeo3d-public` holds model releases, app builds, `config.json` (`min_build` switches old builds off), and the legal pages (Pages from `docs/`).
- Copy new model releases with its "Copy a release" workflow.
- Edit legal texts in `android/app/src/main/assets/legal/`, run `convert/legal_pages.py`, copy the html files to makeo3d-public `docs/`. Bump `Settings.TERMS_VERSION` on material changes.
