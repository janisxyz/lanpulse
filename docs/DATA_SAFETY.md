# Play Console — Data safety answers

Does your app collect or share user data? **No**

Google’s form means data that leaves the device. LanPulse keeps scan results, names, and optional SSH passwords on the phone only.

## Data types

Leave every collection / sharing box unchecked.

Do **not** tick Location as collected. Location permission is used only on-device to read the Wi-Fi SSID.

## Security practices

- Data is encrypted in transit: **Not applicable** (no developer backend). SSH to a host you choose uses the SSH protocol.
- Users can request that data be deleted: **Not applicable** (uninstall the app)
- Independent security review: **No**

## Account

- App requires an account: **No**

## Follow-ups

If Console asks why location is in the manifest: Android needs it to read SSID / BSSID. We do not track the user and do not send coordinates.
