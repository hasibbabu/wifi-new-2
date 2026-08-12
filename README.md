# FreeNet Android Bridge — v0.1

This Android module is a real starter module, not a fake browser bridge.

## Included

- Android application module
- Kotlin
- Runtime permission request
- Bluetooth discovery starter
- Native FreeNetBridge class
- Mesh service placeholder
- Android-side integration point for the Django/PWA layer

## Current transport status

### Bluetooth Classic
Discovery starter is included.

### BLE
Permission/API foundation is included, but GATT advertising/scanning is not yet the complete mesh transport.

### Wi-Fi LAN
The app can later use normal Android networking sockets when devices are on the same LAN.

### Wi-Fi Direct
A dedicated WifiP2pManager implementation still needs to be added.

### Hotspot
Hotspot control is Android-version/device dependent. The app must use APIs permitted by the target Android version and device. It should not assume an app can silently enable/disable hotspot.

## Important architecture

Django:
    accounts + mesh + api
            |
            v
       HTTPS/REST/WebSocket
            |
            v
Android FreeNetBridge
    |       |       |
 Bluetooth Wi-Fi   Wi-Fi Direct
    |       |       |
    +-------+-------+
            |
       Mesh Router

The Android bridge should own radio operations. Django should own accounts, persistent network data, synchronization and server-side coordination.
