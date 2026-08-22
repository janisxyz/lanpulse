# Play Console — Data safety answers

**Does your app collect or share any of the required user data types?** → **No**

Google’s form means data that *leaves the device* (including to the developer or to SDKs). LanPulse keeps scan results, names, theme, and optional SSH passwords on the phone only. Nothing is transmitted to us.

## Data types

Leave **every** collection / sharing box unchecked, including:

- Location
- Personal info
- Financial
- Health
- Messages
- Photos and videos
- Audio
- Files and docs
- Calendar
- Contacts
- App activity
- Web browsing
- App info and performance
- Device or other IDs

Do **not** tick Location as collected. Location permission is used only on-device so Android will reveal the Wi-Fi SSID.

## Security practices

Shown only if you declared collection. If Console still asks:

- Data is encrypted in transit: **Not applicable** (no developer backend). SSH to a host you choose uses SSH.
- Users can request that data be deleted: **Not applicable** (uninstall / clear storage).
- Independent security review: **No**
- Committed to Play Families: **No**

## Account

- App requires an account: **No**

## Follow-ups

If Console asks why location is in the manifest: Android needs it to read SSID / BSSID. We do not track the user and do not send coordinates.

If Console asks about `INTERNET`: LAN probes and SSH to hosts on attached networks only. No developer URL.
