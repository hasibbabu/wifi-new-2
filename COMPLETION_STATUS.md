# FreeNet v5 — remaining implementation status

This package adds the code layers that were still missing in the previous package.

Added:

- Stream frame codec
- Bluetooth Classic RFCOMM client/server
- BLE packet fragmentation/reassembly
- BLE GATT server/client foundations
- ECDH session-key derivation
- AES-GCM encrypted payload helper
- Android Keystore signing identity
- Wi-Fi Direct endpoint helper
- Local Wi-Fi interface detection
- Foreground mesh service
- Persistent packet queue
- LAN discovery
- Route/neighbor management
- Django heartbeat/config endpoints

Important limitations:

1. Android may restrict background radio operations depending on version, OEM and permissions.
2. Hotspot enable/disable cannot be treated as silently controllable on every Android version/device.
3. Wi-Fi Direct group formation and peer endpoint negotiation remain device/API dependent.
4. A production cryptographic handshake still needs a trust model (TOFU, QR pairing, server-issued identity, or another explicit model).
5. The code has NOT been physically tested on Android devices in this package.
6. 1,000-device behavior remains an engineering target, not a verified result.

No test results are claimed.
