# Shared implementation

The shared build contains platform-neutral behavior consumed directly by SyncDroid-Mesh, SyncTosh and SyncDows.

- `mesh-protocol`: version vectors, canonical signed payloads, mesh/pairing/peer/session/index/transfer models, bounded wire codecs, identity helpers, identifiers, and legacy compatibility.
- `sync-core`: file conflict decisions, content-block manifests, hashing, safe paths, index reconciliation, acknowledgements, and resumable-transfer progress.
- `desktop-ui`: reserved for reusable Compose desktop screens and components for SyncTosh and SyncDows.

Run the independent shared test suite with:

```sh
./gradlew test
```

Golden values in `../protocol/fixtures` lock payload and wire bytes, identifiers, hashes, and sync decisions. Android and macOS adapter tests consume the same fixtures.
