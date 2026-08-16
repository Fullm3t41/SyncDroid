# SyncDroid mesh suite

This repository contains the equal-peer, local-Wi-Fi SyncDroid mesh applications. There is no permanent host: SyncDroid, SyncTosh, and the planned SyncDows application use the same mesh membership, synchronization, conflict, history, and chat semantics.

## Applications

| Directory | Product | Platform | Status |
| --- | --- | --- | --- |
| `apps/android` | SyncDroid | Android 10+ | Active |
| `apps/macos` | SyncTosh | Apple Silicon macOS 13+ | Preview |
| `apps/windows` | SyncDows | Windows | Planned |

Each existing application keeps its own Gradle wrapper so it can be built and tested independently while the common implementation is extracted incrementally.

## Shared boundaries

- `shared/mesh-protocol` owns canonical payloads, signatures, wire codecs, and compatibility rules.
- `shared/sync-core` owns version vectors, reconciliation, conflict decisions, hashing, and resumable transfers.
- `shared/desktop-ui` will hold Compose UI shared by SyncTosh and SyncDows.
- `protocol` contains stable schemas and cross-platform compatibility fixtures.

Platform projects must not independently change wire formats or synchronization decisions once those components move into `shared`.

## Build SyncDroid

```sh
cd apps/android
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `apps/android/app/build/outputs/apk/debug/app-debug.apk`.

## Build SyncTosh

```sh
cd apps/macos
./gradlew test packageDmg
```

The DMG is written beneath `apps/macos/build/compose/binaries/main/dmg`.

See `docs/design-plan.md` for the Android product plan, `apps/macos/docs/design-plan.md` for the macOS plan, and `docs/cross-platform-protocol.md` for the interoperability contract.
