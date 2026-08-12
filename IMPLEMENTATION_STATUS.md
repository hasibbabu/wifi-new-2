# FreeNet v4 implementation status

This release adds implementation layers rather than test claims.

Implemented in code:

- Common protocol constants and UUIDs
- Versioned JSON mesh envelope
- TTL and hop count handling
- Android Keystore EC signing identity
- Public-key representation
- Peer signature verification utility
- Neighbor registry
- Route table with multiple candidate hops
- Persistent local packet queue
- LAN UDP discovery
- LAN TCP packet transport
- BLE GATT server
- BLE GATT client starter
- Bluetooth discovery layer
- Wi-Fi Direct discovery foundation
- Central MeshEngine
- Transport coordinator and route-based selection
- Basic forwarding path
- Django heartbeat helper

Still inherently device/API dependent:

- Full Bluetooth RFCOMM socket/session implementation
- Complete Wi-Fi Direct group-owner negotiation and endpoint exchange
- BLE MTU-aware fragmentation/reassembly
- Full authenticated handshake using the signing keys
- Production-grade end-to-end payload encryption/key exchange
- Automatic route advertisements across all radio transports
- Android background-service policy implementation for every supported Android version
- Hotspot control, which is constrained by Android/device APIs
- Real 1,000-device deployment validation

No claim is made that this ZIP has been tested on physical devices.
