# Shared implementation

The shared area is split by responsibility so security-sensitive protocol behavior and synchronization decisions do not diverge between applications.

- `mesh-protocol`: canonical encoding, signed payloads, pairing/session messages, and wire compatibility.
- `sync-core`: indexes, version vectors, conflict decisions, history, hashing, and resumable transfer state.
- `desktop-ui`: reusable Compose desktop screens and components for SyncTosh and SyncDows.

These directories are architectural boundaries at present. Code should move into them only with byte-for-byte protocol fixtures and Android/macOS interoperability tests in place.
