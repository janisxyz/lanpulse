# Publish LanPulse on Google Play

Privacy policy URL (paste this into Play Console → App content → Privacy policy):

**https://janisxyz.github.io/lanpulse/**

Package: `com.lanpulse.app`  
Default language: English (United States)  
Category: **Tools**  
Tags: network, wi-fi, scanner, ssh, lan  
Contact email: `shizoghost@exdonuts.com`  
Website: `https://janisxyz.github.io/lanpulse/`

Sideload APKs: [GitHub Releases](https://github.com/janisxyz/lanpulse/releases)

---

## 0. Do not upload a debug-signed AAB

GitHub Actions signs with the **debug** keystore unless the four `LANPULSE_*` secrets are set. A debug upload key is public knowledge — Play will accept the first AAB as the upload key and you will regret it.

Create an upload key **once**, store it only as GitHub secrets, then run **Actions → Release**. Use that AAB.

```bash
keytool -genkeypair -v \
  -keystore lanpulse-upload.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias lanpulse \
  -dname "CN=Janis Schelling, OU=LanPulse, O=shizoghost, L=Basel, C=CH"

base64 -w0 lanpulse-upload.jks > lanpulse-upload.b64
```

GitHub → repo **Settings → Secrets and variables → Actions**:

| Secret | Value |
|--------|--------|
| `LANPULSE_KEYSTORE_BASE64` | contents of `lanpulse-upload.b64` |
| `LANPULSE_KEYSTORE_PASSWORD` | keystore password |
| `LANPULSE_KEY_ALIAS` | `lanpulse` |
| `LANPULSE_KEY_PASSWORD` | key password |

Keep `lanpulse-upload.jks` in a password manager. Never commit it.

After secrets exist, **Actions → Release → Run workflow** (bump `none` if you do not want a version bump, or `patch`). Confirm the release notes say `signing: upload keystore`. Download `LanPulse-x.y.z.aab`.

---

## 1. Developer account

- Pay the Play one-time registration fee.
- Identity verification (government ID / D-U-N-S for orgs) must be complete.
- Personal accounts created after 13 Nov 2023 need a **closed test with at least 12 testers for 14 days** before production.

## 2. Create the app

Play Console → **Create app**

- App name: **LanPulse**
- Default language: English (United States)
- App or game: **App**
- Free or paid: **Free**
- Declarations: accept Play policies, US export laws, Play Families if shown (this is **not** a families app)

Package name is locked to `com.lanpulse.app` on first AAB upload. Do not create a different one.

## 3. Play App Signing

On first upload: enroll **Play App Signing** (required for new apps). Upload the AAB from step 0. Google holds the *app signing* key; your JKS is only the *upload* key.

## 4. Store listing copy

Paste from `fastlane/metadata/android/en-US/`.

| Field | Limit | File |
|-------|--------|------|
| Title | 30 | `title.txt` → **LanPulse** |
| Short description | 80 | `short_description.txt` |
| Full description | 4000 | `full_description.txt` |

Graphics (upload from `store/` or `fastlane/metadata/android/en-US/images/`):

| Asset | Spec | File |
|-------|------|------|
| High-res icon | 512×512 32-bit PNG, **no rounded mask** | `store/icon-512.png` |
| Feature graphic | 1024×500 JPEG/PNG, **no alpha** | `store/feature-1024x500.png` |
| Phone screenshots | at least 2, 16:9 or 9:16, 320–3840 px | `store/screenshots/` (1080×1920) |

Optional: 7-inch / 10-inch tablet shots, promo video.

The screenshots in `store/screenshots/` are UI mockups so you can submit. Replace them with captures from a real phone when you can (Play prefers real UI).

## 5. App content — click every card

Use these answers. Details in sibling files.

| Card | Answer |
|------|--------|
| Privacy policy | `https://janisxyz.github.io/lanpulse/` |
| Ads | **No** |
| App access | All features available without restriction / no login |
| Content ratings | IARC questionnaire → Utility. See `CONTENT_RATING.md` |
| Target audience | **13 and up** (not a kids app, not in Designed for Families) |
| News apps | **No** |
| COVID-19 apps | **No** |
| Data safety | **Does not collect** / **does not share**. See `DATA_SAFETY.md` |
| Government apps | **No** |
| Financial features | **No** |
| Health | **No** |
| Location | See below |

### Location permission declaration

Play will flag `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`.

- Why: **Wi-Fi connection information** (SSID / BSSID for the Discover header).
- Not used for: advertising, analytics, tracking, maps, geofencing, background location.
- Foreground only.
- On Android 13+ we also request `NEARBY_WIFI_DEVICES` with `neverForLocation`.

If Console offers “network management” / “Wi-Fi connection information”, pick that.

### Photos / videos / music

None. Do not declare.

### Health Connect / Foreground services / Advertising ID

None. Advertising ID: **No**.

## 6. Target API

`compileSdk` / `targetSdk` are **36** (Android 16). Required for new Play uploads from **31 August 2026**.

## 7. Closed testing (required for new personal accounts)

1. **Internal testing** — upload the AAB, add your own Google account, install from the opt-in link. Confirm it launches, scans the LAN, SSH form shows.
2. **Closed testing** — create a track (e.g. `closed`), upload the same AAB, add **at least 12 testers** (Gmail addresses). They must opt in and keep it installed **14 days**.
3. After the 14-day / 12-tester bar is green, apply for **production**.

Testers: friends, colleagues, GitHub issue watchers. They need a Play account in the same country you ship.

## 8. Production checklist

- [ ] Privacy URL returns 200 (no login wall)
- [ ] Data safety matches this policy (collect = No)
- [ ] IARC rating generated
- [ ] High-res icon + feature graphic + ≥2 phone screenshots
- [ ] AAB signed with **upload** keystore (`signing: upload keystore` in the GitHub Release)
- [ ] Play App Signing on
- [ ] Closed test completed (if your account type requires it)
- [ ] Store listing in en-US saved
- [ ] Countries / pricing: Free, distribute where you want
- [ ] Content rating applied to the app

Then **Send for review**. First review often takes a few days.

## 9. After you ship

Each push to `main` that is not `[skip release]` cuts a new GitHub Release (APK + AAB). Upload the new AAB to the Play track (internal → closed → production). `VERSION_CODE` is monotonic; Play rejects reuse.

Do not lose the upload JKS. If you do, Play Console → App signing → **Request upload key reset**.
