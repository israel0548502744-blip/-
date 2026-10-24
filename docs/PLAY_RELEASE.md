# Before publishing on Google Play

Status 2026-10-24. Items marked ✅ are done in the app; the rest need the owner.

## Legal documents
- ✅ Terms of use (Hebrew) in the app: Settings → משפטי ופרטיות, and accepted once on first launch. Source: `app/src/main/assets/legal/terms_he.txt`, copy in `docs/TERMS.md`.
- ✅ Privacy policy (Hebrew + English) in the app and in `docs/PRIVACY.md`.
- ☐ **Contact e-mail** in both documents (placeholder `[כתובת מייל ליצירת קשר]`). Play requires a working one.
- ☐ **Public URL for the privacy policy.** Play asks for a link. Options: GitHub Pages from this repo (free), or Google Sites. The text is ready in `docs/PRIVACY.md`.
- ☐ Lawyer review: `docs/LAWYER_BRIEF.md` is the message to send.

## App changes needed for the Play build
- ☐ **New name and package id.** `com.triposr.mobile` → the new name. A package id can never change after the first upload.
- ☐ **Remove the self-updater from the Play build.** Play forbids apps that download and install their own APKs, and `REQUEST_INSTALL_PACKAGES` is a restricted permission. Plan: a `play` build flavor without `Updater` and without that permission; keep the GitHub build for testing.
- ☐ **Release signing key** stored safely (Play App Signing). The current keystore in the repo is for test builds only.
- ☐ **AAB instead of APK.** Play needs an Android App Bundle. The models are already downloaded after install, so the bundle stays small (~100 MB with the QNN libraries).
- ☐ Model downloads come from GitHub releases. That's allowed, but for a paid product a CDN under our control is safer (rate limits, availability). Cloudflare R2 has free egress.
- ☐ Target API level: Play requires the latest-but-one Android API. Check `targetSdk` in `app/build.gradle.kts` before upload.

## Play Console forms (answers prepared)
- **Data safety:** "No data collected", "No data shared". Crash logs stay on the device and are only shared manually by the user. Data is encrypted in transit (HTTPS downloads). Users can delete data: yes (in-app clear + uninstall).
- **Content rating (IARC):** no violence, no user-to-user communication, no ads. Expect rating 3+/Everyone.
- **Target audience:** 13+ (avoid the "Designed for Families" requirements).
- **Ads:** none.
- **Foreground service declaration:** `specialUse`: "on-device 3D generation that continues while the user leaves the app". A short video of the feature may be requested.
- **Account deletion:** not applicable (no accounts).
- **Financial features / health / government:** none.

## Store listing
- ☐ Name (≤30 chars), short description (≤80), full description (≤4000), Hebrew + English.
- ☐ Icon 512×512, feature graphic 1024×500, at least 2 phone screenshots. The CI screenshots (`ui-screenshots` branch) can be the start.
- ☐ Don't show copyrighted characters (Disney, Marvel…) in screenshots or ads.
- ☐ Developer account ($25 once) and, for selling in the EU, the DSA trader details (address, phone, e-mail are shown publicly).

## Money
- ☐ Decide: paid app, or free with a one-time unlock (Play Billing). Payments must go through Play Billing.
- ☐ Payments profile in Play Console, bank account, tax info (Israel: a sole trader file (עוסק פטור/מורשה) is needed to receive the payments).
