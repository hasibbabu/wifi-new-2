# FreeNet Android — Messaging, Media & Calling (this pass)

This pass wires the previously-scaffolded mesh transports into an actually
end-to-end path: text, photos, voice notes flow hop-by-hop over whichever
transport is available; direct (1-hop) audio/video calls layer on top once
two phones share a live radio link.

## What changed and why

**Bug fix — packets were unroutable end-to-end.** Transports (`WifiLanTransport`,
`BleTransport`, `BluetoothTransport`, `WifiDirectTransport`) serialized outgoing
data with the old `MeshPacket.toJson()` schema, but `FreeNetBridge.receive()`
always decoded with `MeshEnvelope.decode()`, a different JSON schema. Every
packet sent over any transport would have failed to decode on arrival. Fixed
by making every transport speak `MeshEnvelope.encode()`/`decode()` bytes
directly; `MeshPacket.kt` now only holds `SendResult`.

**Bug fix — no duplicate/loop protection.** `PacketStore` existed but was
never wired in, so a flooded broadcast would loop forever between neighbors.
`MeshEngine.shouldProcess()` now dedupes by `packetId` before a packet is
delivered or forwarded.

**New: application-level message protocol** (`protocol/MessageBody.kt`) —
`text`, `file_meta` / `file_chunk` (chunked photo/voice-note transfer),
`file_ack`, and `call_invite` / `call_accept` / `call_reject` / `call_end` /
`call_endpoint` for call signaling. This sits above `MeshEnvelope`, which
only carries routing metadata.

**New: chunked file transfer** (`mesh/media/`) — `MediaChunker` splits a
photo/voice-note into a `FileMeta` announcement + `FileChunk` pieces sized
to fit one `MeshEnvelope`; `MediaReassembler` reassembles them out-of-order
(mesh hops can reorder/duplicate) with a SHA-256 integrity check; `MediaStore`
saves received files to app-specific storage and reads picked files for
sending. Each chunk is its own envelope, so a mid-transfer disconnect just
leaves the remaining chunks in the existing offline queue.

**New: BLE central+peripheral duality** — `FreeNetAdvertiser` (advertises
the service UUID so scans can find this device), rewritten `FreeNetGattServer`
(per-device fragment reassembly) and `FreeNetGattClient` (subscribes to
notifications, sends fragmented writes), tied together in a rewritten
`BleTransport` that does a small HELLO handshake over the link so routing
can address peers by mesh node id instead of a raw BLE address.

**New: Bluetooth Classic wiring** — rewritten `BluetoothTransport` connects
out via `RfcommTransport` on `ACTION_FOUND`, runs `RfcommServer` for inbound
connections, same HELLO handshake pattern as BLE.

**New: Wi-Fi Direct wiring** — rewritten `WifiDirectTransport` handles peer
discovery + auto-connect; once a P2P group forms, the *existing* UDP
discovery (`LanDiscovery`) and TCP transport (`WifiLanTransport`, whose
`ServerSocket` already listens on all interfaces) take over automatically —
no separate wire format needed for the Wi-Fi Direct data plane.

**New: direct (1-hop) audio/video calling** (`mesh/call/`) — call signaling
(`DirectCallManager`) rides the mesh like any other message and can ring a
phone several hops away; once accepted, both sides exchange direct IP:port
endpoints and open a UDP media socket between them
(`AudioCallStreamer` for 16kHz PCM voice, `VideoCallStreamer` for ~320x240
JPEG frames at ~8fps via Camera2). `CallActivity` provides ring/accept/reject
UI, local preview (TextureView), and remote frame rendering.

**New: chat/media/call UI** — `MainActivity` now has a destination-node-id
field, a scrolling message log, send-text/send-photo/hold-to-record-voice
buttons, and audio/video call buttons; `FreeNetApp` (new `Application`
subclass) holds one shared `FreeNetBridge` + `DirectCallManager` so the mesh
and any in-progress call survive across activities (an invite can arrive
while no call screen is open). `FreeNetMeshService` now uses that shared
bridge instead of spinning up a second one that would fight the first over
the same ports.

## Why calls are capped at one hop (by design, not an oversight)

BLE tops out around 1-4 KB/s of real throughput; even Bluetooth Classic and
early Wi-Fi Direct negotiation add real latency. Relaying *live* audio/video
through several intermediate phones stacks that latency and loss at every
hop until it's unusable — no mesh radio technology does this well. So: call
signaling (invite/accept/ring) flies through the mesh normally and can reach
someone several hops away, but the media itself only starts once both phones
have a direct IP link (same Wi-Fi/hotspot, or a freshly-formed Wi-Fi Direct
group). Out of range for a direct link → the call fails to connect and the
app should suggest recording a voice note instead (data layer is identical
either way).

## What still needs real-device testing

This was written and reasoned through carefully, but there is no Android
SDK/emulator in this environment to compile or run it against real
hardware — please build it in Android Studio and test on at least two
physical devices before relying on it:

- BLE GATT write pacing: `FreeNetGattClient.send()` fires fragment writes
  back-to-back rather than waiting for each `onCharacteristicWrite`
  callback before the next — noted in-code; may drop fragments on some
  BLE stacks under load.
- Wi-Fi Direct auto-connect (`WifiDirectTransport.onPeersChanged`) will
  attempt `WifiP2pManager.connect()` on every newly seen peer, which can
  trigger a system pairing dialog on some OEM builds — expected P2P
  behavior, but worth confirming on your target devices.
- `VideoCallStreamer`'s YUV_420_888 → NV21 conversion accounts for
  rowStride/pixelStride, but chroma-plane layout varies enough across
  vendors that it's worth confirming colors look right on your test
  devices.
- Bluetooth Classic connections use `createRfcommSocketToServiceRecord`
  (secure), which may prompt a pairing dialog the first time two devices
  connect — expected, but affects the "fully automatic" discovery story.
- Runtime permission flows (`AndroidPermissions`, `CallActivity`'s
  in-call permission check) request everything up front; you may want to
  defer camera/mic requests until the user actually starts a call for a
  smoother first-run experience.
