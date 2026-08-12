# FreeNet Android Architecture

## Transport interface

Future transports should implement a common interface:

- discover()
- connect()
- disconnect()
- send()
- receive()
- isAvailable()

Candidate transports:

1. Wi-Fi LAN
2. Wi-Fi Direct
3. Hotspot/local AP
4. Bluetooth Classic
5. BLE
6. Internet fallback

## Multi-hop

The Android layer should not simply broadcast every message.

A packet should carry:

- packet_id
- source_node
- destination_node
- ttl
- hop_count
- route metadata
- payload

Each node forwards only when the routing layer selects it.

## Security

Before production:

- Use authenticated device identities.
- Use modern end-to-end encryption.
- Authenticate every peer.
- Prevent replay.
- Enforce TTL and packet-size limits.
- Rate-limit discovery and forwarding.
- Do not trust a device merely because it is nearby.
