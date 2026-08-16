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

- `shared/mesh-protocol` owns version vectors, signed event payloads, identity helpers, and all deployed mesh/pairing/session/index/transfer wire codecs shared by Android and macOS.
- `shared/sync-core` owns file conflict decisions, block hashing, safe paths, index reconciliation, acknowledgements, and resumable-transfer progress.
- `shared/desktop-ui` will hold Compose UI shared by SyncTosh and SyncDows.
- `protocol` contains stable schemas and cross-platform compatibility fixtures.

Golden fixtures under `protocol/fixtures` prevent either platform from silently changing shared payload or wire bytes, identifiers, hashes, or conflict outcomes.

## Test the shared core

```sh
cd shared
./gradlew test
```

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

For a Windows implementation implementation notes, see [`WINDOWS_BUILD_PLAN.md`](WINDOWS_BUILD_PLAN.md).
