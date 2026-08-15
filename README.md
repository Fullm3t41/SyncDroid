# SyncDroid

SyncDroid is a local Wi-Fi-only file-sync concept with an Android-first client. Devices form an equal-peer mesh: there is no permanent host, cloud account, or internet dependency. The protocol boundary is platform-neutral so future Windows, Linux, and macOS clients can join as equal peers.

The Android client targets Android 10 and newer and includes authenticated local discovery, replicated mesh metadata, and peer file transfer.

## Included in the prototype

- Calm, neutral light and dark themes
- Five-tab Sync, Folders, Devices, Chat, and Settings navigation
- Hub-and-spoke local mesh with the current device in the centre and no peer-to-peer line crossings
- Expandable save-folder cards and a visible conflict-review state
- Built-in file manager with folder creation when Android grants All files access
- Automatic fallback to Android's folder picker when broad access is unavailable or declined, including Android 10
- Per-folder include and exclude filters with glob patterns such as `*.sav`
- Wi-Fi-specific power rules, configurable discovery intervals, coordinated rendezvous times, and five-minute discovery windows
- Persistent multi-network Wi-Fi allowlist that switches sync on for any enabled SSID and pauses it elsewhere
- Generated mesh-inspired launcher icon with adaptive, legacy-density, and Play Store assets
- Unit-tested discovery-window schedule calculations
- Standard UTF-8, versioned mesh metadata with legacy Android decoding
- Draft cross-platform Protobuf contract for future Windows, Linux, and macOS clients
- Signed, host-free mesh group chat replicated during authenticated local rendezvous sessions

## Build

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

See `docs/design-plan.md` for the product plan and `docs/cross-platform-protocol.md` for the interoperability contract.
