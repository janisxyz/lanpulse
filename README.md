# LanPulse

Material You LAN scanner for Android — auto-detects every subnet on the phone, sweeps them in parallel, fingerprints hosts, shows live ping, and runs WiFiman-style quick / deep port scans.

**Repo:** [github.com/janisxyz/lanpulse](https://github.com/janisxyz/lanpulse)  
**Privacy policy:** [janisxyz.github.io/lanpulse](https://janisxyz.github.io/lanpulse/)  
**Play listing notes:** [docs/PLAY_STORE.md](docs/PLAY_STORE.md)  
**Data safety answers:** [docs/DATA_SAFETY.md](docs/DATA_SAFETY.md)


## What it does (better than a single-/24 ping sweep)

- **Every range, automatically.** Walks `ConnectivityManager` for Wi-Fi, Ethernet, VPN, hotspot / USB tethering. Each IPv4 `LinkAddress` becomes a CIDR. Huge prefixes (`/8`, `/16`) are capped to the local `/24` so you don’t scan 16 million hosts by accident.
- **Parallel host discovery.** Up to 64 probes at once: `/system/bin/ping` first, TCP fallback on 80/443/22/445/53 if ICMP is blocked.
- **Device intel.** ARP / `ip neigh` MAC, compact OUI vendor table, reverse DNS hostname, open-port fingerprint (NAS, printer, camera, Cast, HA, Apple lockdownd…).
- **Ping.** RTT from the ping binary or TCP handshake, shown on the list and host sheet.
- **Quick + deep port scans.** Curated 20-port “quick” profile and a 50+ service deep scan with optional banner grab.
- **Material You.** Dynamic color on Android 12+, teal seed, Discover / Radar / Host.

## Open in Android Studio

1. Clone this repo.
2. **File → Open** the project root.
3. Let Gradle sync (wrapper is **Gradle 8.9**).
4. Plug in a phone or start an emulator (API 26+). Run `app`.
5. Grant **location** (needed to read SSID / BSSID on modern Android) when asked.

```bash
git clone https://github.com/janisxyz/lanpulse.git
cd lanpulse
./gradlew assembleDebug
```

Minimum SDK 26, target / compile SDK 36, Kotlin 2.0, Jetpack Compose + Material 3.

## Releases (CI)

GitHub Actions builds a **sideload APK** and a **Play AAB** on every qualifying push to `main`, then publishes a GitHub Release.

| Workflow | When | What |
|---|---|---|
| [CI](.github/workflows/ci.yml) | PR + push | `assembleDebug`, upload artifact |
| [Release](.github/workflows/release.yml) | push to `main`, or **Run workflow** | bump semver, tag `vX.Y.Z`, `assembleRelease` + `bundleRelease`, GitHub Release |

**Versioning** lives in [`version.properties`](version.properties):

- `VERSION_NAME` — semver (`1.0.0`)
- `VERSION_CODE` — integer, always +1 per release (Play requirement)

Auto-bump is **patch** on each `main` push. First release keeps `1.0.0`. Manual run: **Actions → Release → Run workflow** and pick `patch` / `minor` / `major` / `none`. Commits with `[skip release]` in the message are ignored.

Put this in the commit that should not cut a release:

```text
[skip release]
```

### Play signing (optional)

Without secrets, artifacts are signed with the Android **debug** keystore (installable, not for Play). For Play Console, add repository secrets:

| Secret | Value |
|---|---|
| `LANPULSE_KEYSTORE_BASE64` | `base64 -w0 upload-keystore.jks` |
| `LANPULSE_KEYSTORE_PASSWORD` | keystore password |
| `LANPULSE_KEY_ALIAS` | key alias |
| `LANPULSE_KEY_PASSWORD` | key password |

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | ICMP / TCP probes |
| `ACCESS_NETWORK_STATE` | Enumerate interfaces & CIDRs |
| `ACCESS_WIFI_STATE` | SSID, RSSI, frequency, link speed |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS-friendly multicast |
| `ACCESS_FINE_LOCATION` / `NEARBY_WIFI_DEVICES` | SSID on Android 8–13+ |

LanPulse only scans **your** attached networks. It does not target the public internet.

## Layout

```
app/src/main/java/com/lanpulse/app/
  MainActivity.kt
  scan/          CIDR math, range detect, ping, ports, ARP, vendors, classify
  ui/            Compose screens + ViewModel
  ui/theme/      Material You seed + dynamic color
```
