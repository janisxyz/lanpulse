# LanPulse

Material You LAN scanner for Android — auto-detects every subnet on the phone, sweeps them in parallel, fingerprints hosts, shows live ping, and runs WiFiman-style quick / deep port scans.

**Repo:** [github.com/janisxyz/lanpulse](https://github.com/janisxyz/lanpulse)

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
3. Let Gradle sync. If the wrapper JAR is missing, Android Studio offers *Create Gradle Wrapper* — accept it (Gradle **8.9**).
4. Plug in a phone or start an emulator (API 26+). Run `app`.
5. Grant **location** (needed to read SSID / BSSID on modern Android) when asked.

```bash
git clone https://github.com/janisxyz/lanpulse.git
cd lanpulse
# then open the folder in Android Studio
```

Minimum SDK 26, target 35, Kotlin 2.0, Jetpack Compose + Material 3.

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
