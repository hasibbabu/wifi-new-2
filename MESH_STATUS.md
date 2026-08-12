# FreeNet v3

Added native transport foundations:

- Wi-Fi LAN TCP packet transport
- Wi-Fi Direct peer-discovery foundation
- BLE scanner foundation
- Bluetooth discovery foundation
- Common packet format
- TTL/hop count
- Duplicate packet cache
- Route table
- Transport coordinator

Still required before claiming a complete production 1000-node mesh:

- BLE GATT service/characteristics
- Bluetooth RFCOMM connection management
- Wi-Fi Direct group formation and endpoint exchange
- Automatic neighbor identity exchange
- Signed/authenticated node identities
- End-to-end encryption
- Route advertisement and route expiry
- Persistent store-and-forward
- Android background execution hardening
- Real-device multi-hop and 1000-node stress testing

The current code is deliberately modular so these pieces can be added without replacing the Django architecture.
