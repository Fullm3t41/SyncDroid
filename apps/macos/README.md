# SyncTosh

SyncTosh is the self-contained Apple Silicon macOS peer for the SyncDroid-Mesh network.

The current preview includes the native macOS application, Android-compatible mesh pairing, folder synchronization, resumable transfers, file history, and signed mesh chat. Later parity phases follow [`docs/design-plan.md`](docs/design-plan.md).

Requirements:

- macOS 13 or newer
- Apple Silicon (ARM64); Intel Macs are not supported

At wide window sizes, Sync, Folders, Devices, and Settings automatically reflow into desktop columns. Chat intentionally remains a single conversation column.

The SyncTosh icon is derived from the SyncDroid-Mesh artwork, with a macOS-inspired multicolour centre to distinguish the desktop app.

## Implemented mesh foundation

- P-256 device identity retained in an owner-only local identity file, with one-time migration of existing Keychain identities
- SQLite mesh profile, membership log, trusted-device projection, and separately pinned TLS keys
- Android-compatible Bonjour and UDP pairing discovery
- Transcript-authenticated six-digit J-PAKE pairing
- Five attempts per rolling 15-minute attempt window
- Android-compatible signed membership and mesh-bundle codecs, including verification of immutable membership-v1 histories created by early SyncDroid-Mesh builds
- Signed Android folder announcements persisted as per-Mac Configure/Configured/Declined entries
- Existing-folder selection and new local folder creation for received mesh folders
- Signed TLS-bound identity proofs and returning-peer synchronization sessions
- Local-network privacy and Bonjour service declarations in the macOS bundle
- Per-file indexes with version vectors, tombstones, received/applied acknowledgements, and conflict detection
- Hash-verified whole-file and resumable block transfer with atomic file application
- Thirty-day deletion recovery and file history
- Signed, persisted mesh chat replicated through trusted peers
- Open configured folders directly in Finder
- Menu-bar background operation with midnight-aligned discovery intervals and configurable windows

## Development

```bash
./gradlew run
```

## Package the macOS application

```bash
./gradlew packageDmg
```
