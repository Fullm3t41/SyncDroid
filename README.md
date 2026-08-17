# SyncDroid-Mesh

<p align="center">
  <img src="https://fullm3t41.github.io/SyncDroid-Mesh/design/icon/syncdroid-icon-source.png" width="128" alt="SyncDroid-Mesh Android app icon">
  &nbsp;&nbsp;&nbsp;
  <img src="https://fullm3t41.github.io/SyncDroid-Mesh/design/icon/synctosh-icon-source.png" width="128" alt="SyncTosh macOS app icon">
  &nbsp;&nbsp;&nbsp;
  <img src="https://fullm3t41.github.io/SyncDroid-Mesh/design/icon/syncdows-icon-source.png" width="128" alt="SyncDows Windows app icon">
</p>

<p align="center"><strong>SyncDroid-Mesh · SyncTosh · SyncDows</strong></p>

**Private, peer-to-peer folder sync for the devices on your local Wi-Fi.**

SyncDroid-Mesh connects Android, macOS and Windows devices as an equal-peer mesh. There is no permanent host, central server or required account: any trusted device can discover another device, exchange the latest folder state and relay verified files when both are available on the same local network.

> SyncDroid-Mesh is under active development. Use the preview releases with files you can recover elsewhere while cross-platform testing continues.

[Download the latest release](https://github.com/Fullm3t41/SyncDroid-Mesh/releases/latest)

## Preview

| SyncDroid-Mesh · Android | SyncTosh · macOS |
| --- | --- |
| ![SyncDroid-Mesh local mesh sync screen](https://fullm3t41.github.io/SyncDroid-Mesh/design/screenshots/syncdroid-mesh-sync.png) | ![SyncTosh folder synchronization guide](https://fullm3t41.github.io/SyncDroid-Mesh/design/screenshots/synctosh-folders.png) |
| ![SyncDroid-Mesh folder management screen](https://fullm3t41.github.io/SyncDroid-Mesh/design/screenshots/syncdroid-mesh-folders.png) | ![SyncTosh settings screen](https://fullm3t41.github.io/SyncDroid-Mesh/design/screenshots/synctosh-settings.png) |

## What it does

- **Decentralised local mesh.** Every device is an equal peer. The mesh continues without an Android phone, Mac or PC acting as a permanent host.
- **Folder synchronization.** Add a local folder, choose optional include and exclude filters such as `*.sav`, and configure its location independently on every device.
- **Offline catch-up.** Devices exchange indexes and acknowledgements whenever they meet again, even after being offline for days or weeks.
- **Resumable transfers.** Large files are verified in blocks and interrupted transfers can continue instead of starting again.
- **Conflict review.** Concurrent edits are compared using version vectors, content hashes and parent hashes. Keep either version or preserve both with a numbered filename.
- **Safe deletion behavior.** Standard folders replicate deletions with tombstones. Overwrite-only folders retain copies elsewhere until every participating device reports the file absent, then resolve the exception into a tombstone automatically. Active exceptions can still be undone individually.
- **History and recovery.** Review recent file activity and recover eligible deletions for up to 30 days.
- **Mesh chat.** Trusted devices exchange signed group messages and settle them into chronological order as peers reconnect.
- **Power-aware discovery.** Register multiple Wi-Fi networks and choose background discovery intervals and windows. Foreground discovery remains continuous, and active synchronization is allowed to finish.
- **Light and dark appearance.** The Android and desktop interfaces share the same expandable-card visual language while adapting to each platform.

## How synchronization works

1. **Create a mesh** on the first device.
2. **Add another device** with a short-lived six-digit pairing code shown by an existing member.
3. **Choose folders** and optional file filters. A newly announced folder appears as **Configure** on other devices so each one can choose its own local location or decline it.
4. **Meet on local Wi-Fi.** Devices discover one another with local network discovery, authenticate their pinned identities and exchange signed mesh state.
5. **Transfer only what is needed.** File manifests identify new, changed and deleted content. Downloads are verified before they atomically replace the destination, and received timestamps do not create a false edit loop.
6. **Review ambiguity instead of guessing.** If causal history cannot prove which concurrent edit is newer, SyncDroid-Mesh asks the user which version to keep.

Membership is replicated too. When an authorised device adds or removes a mesh member, that signed change reaches the other trusted devices during later synchronization sessions.

## Security and privacy

- Synchronization is local-first and does not require a SyncDroid-Mesh account or hosted coordination service.
- Six-digit pairing uses a transcript-authenticated password-authenticated key exchange rather than sending the code as a reusable password.
- Trusted sessions use mutually authenticated TLS with pinned device identities.
- Membership changes, folder announcements, chat messages and overwrite-only exceptions are signed.
- Content hashes are verified before downloaded files are applied.
- Device private keys remain in platform-specific protected local storage.

The six-digit code is designed for convenient nearby pairing, not for publishing publicly. Only display it while adding a device you control.

## Applications

| App | Platform | Current status |
| --- | --- | --- |
| **SyncDroid-Mesh** | Android 10 and later | Preview releases available |
| **SyncTosh** | Apple Silicon macOS 13 and later | Preview releases available |
| **SyncDows** | Windows 10/11 | Native testing in progress |
| **SyncDeck** | Linux/SteamOS | Planned |

Release downloads are self-contained: SyncDroid-Mesh is provided as an Android APK, SyncTosh as an Apple Silicon DMG, and SyncDows as a branded Windows EXE. From version 0.2.0 onward, every app checks the signed GitHub release manifest at startup and daily, and can reuse a verified installer cached by a trusted mesh peer.

## Current scope

The local peer mesh, file transfer engine, resumable blocks, history, recovery, chat and conflict foundations are implemented. Google Drive and OneDrive adapters, broader release hardening, code signing/notarization and SyncDeck remain ongoing work.

## Licence

SyncDroid-Mesh, SyncTosh, SyncDows and the shared protocol/synchronization code are licensed under the [GNU General Public License version 3](LICENSE). Distributed modifications must remain under GPLv3 and include the corresponding source.

The application names, logos and icons remain reserved project branding. Forks and independently distributed builds must follow the [trademark policy](TRADEMARKS.md). Third-party components retain their respective licences.
