# Publish LanPulse on Google Play

Privacy policy URL (after Pages deploys):

**https://janisxyz.github.io/lanpulse/**

Package: `com.lanpulse.app`  
Category: Tools  
Default language: English (United States)

## 1. Developer account

- Pay the Play one-time registration fee.
- Personal accounts created after 13 Nov 2023 need a **14-day closed test with at least 12 testers** before production.

## 2. Upload key (once)

```bash
keytool -genkey -v -keystore lanpulse-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias lanpulse
base64 -w0 lanpulse-upload.jks > lanpulse-upload.b64
```

GitHub Actions secrets (never commit the jks):

| Secret | Value |
|--------|--------|
| `LANPULSE_KEYSTORE_BASE64` | contents of `lanpulse-upload.b64` |
| `LANPULSE_KEYSTORE_PASSWORD` | keystore password |
| `LANPULSE_KEY_ALIAS` | `lanpulse` |
| `LANPULSE_KEY_PASSWORD` | key password |

Then run workflow **Release** → download `LanPulse-x.y.z.aab`.

Play Console: Create app → enroll **Play App Signing** → upload that AAB as the first artifact.

## 3. Store listing copy

Paste from `fastlane/metadata/android/en-US/`.

- Title: LanPulse
- Short description: `short_description.txt`
- Full description: `full_description.txt`

## 4. Graphics

| Asset | File |
|-------|------|
| App icon 512×512 | `store/icon-512.png` |
| Feature graphic 1024×500 | `store/feature-1024x500.png` |
| Phone screenshots | `store/screenshots/` (at least two; replace with real-device shots when you can) |

Do **not** round the Play icon. Google applies the mask.

## 5. App content declarations

- Privacy policy: `https://janisxyz.github.io/lanpulse/`
- Data safety: see `docs/DATA_SAFETY.md`
- Content rating: see `docs/CONTENT_RATING.md`
- Ads: no
- Target audience: 13+ utility; not a kids app
- News / COVID / government: no
- Financial features: no
- Health: no

## 6. Target API

`targetSdk` is **35**. From **31 August 2026** new submissions must target **36**. Bump before that date if you have not shipped yet.

## 7. Sensitive permissions

Play will ask why location is declared: **Wi-Fi SSID**. Pick the closest “Wi-Fi connection information” / “network management” reason. Do not claim maps or advertising.

## 8. Closed testing

Upload the AAB to **Internal testing** first, then **Closed testing**. Share the opt-in link with testers. After 12 testers × 14 days you can promote to production.
