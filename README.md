# SyncDroid

**Private, peer-to-peer folder sync for the devices on your local Wi-Fi.**

SyncDroid connects Android, macOS and Windows devices as an equal-peer mesh. There is no permanent host, central server or required account: any trusted device can discover another device, exchange the latest folder state and relay verified files when both are available on the same local network.

> SyncDroid is under active development. Use the preview releases with files you can recover elsewhere while cross-platform testing continues.

[Download the latest release](https://github.com/Fullm3t41/SyncDroid/releases/latest)

## Preview

<table>
  <tr>
    <td align="center"><strong>SyncDroid · Android</strong></td>
    <td align="center"><strong>SyncTosh · macOS</strong></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/readme/syncdroid-sync.png" width="300" alt="SyncDroid local mesh sync screen"></td>
    <td align="center"><img src="docs/images/readme/synctosh-sync.png" width="680" alt="SyncTosh local mesh sync screen"></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/readme/syncdroid-settings.png" width="300" alt="SyncDroid settings screen"></td>
    <td align="center"><img src="docs/images/readme/synctosh-folders.png" width="680" alt="SyncTosh folder management screen"></td>
  </tr>
</table>

## What it does

- **Decentralised local mesh.** Every device is an equal peer. The mesh continues without an Android phone, Mac or PC acting as a permanent host.
- **Folder synchronization.** Add a local folder, choose optional include and exclude filters such as `*.sav`, and configure its location independently on every device.
- **Offline catch-up.** Devices exchange indexes and acknowledgements whenever they meet again, even after being offline for days or weeks.
- **Resumable transfers.** Large files are verified in blocks and interrupted transfers can continue instead of starting again.
- **Conflict review.** Concurrent edits are compared using version vectors, content hashes and parent hashes. Keep either version or preserve both with a numbered filename.
- **Safe deletion behavior.** Standard folders replicate deletions with tombstones. Overwrite-only folders can retain deleted files elsewhere and list per-file exceptions that can be undone.
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
6. **Review ambiguity instead of guessing.** If causal history cannot prove which concurrent edit is newer, SyncDroid asks the user which version to keep.

Membership is replicated too. When an authorised device adds or removes a mesh member, that signed change reaches the other trusted devices during later synchronization sessions.

## Security and privacy

- Synchronization is local-first and does not require a SyncDroid account or hosted coordination service.
- Six-digit pairing uses a transcript-authenticated password-authenticated key exchange rather than sending the code as a reusable password.
- Trusted sessions use mutually authenticated TLS with pinned device identities.
- Membership changes, folder announcements, chat messages and overwrite-only exceptions are signed.
- Content hashes are verified before downloaded files are applied.
- Device private keys remain in platform-specific protected local storage.

The six-digit code is designed for convenient nearby pairing, not for publishing publicly. Only display it while adding a device you control.

## Applications

| App | Platform | Current status |
| --- | --- | --- |
| **SyncDroid** | Android 10 and later | Preview releases available |
| **SyncTosh** | Apple Silicon macOS 13 and later | Preview releases available |
| **SyncDows** | Windows 10/11 | Native testing in progress |
| **SyncDeck** | Linux/SteamOS | Planned |

Release downloads are self-contained: the Android release is provided as an APK and SyncTosh as a macOS DMG. Windows installers will be added to a public release after physical interoperability and installer testing is complete.

## Current scope

The local peer mesh, file transfer engine, resumable blocks, history, recovery, chat and conflict foundations are implemented. Google Drive and OneDrive adapters, broader release hardening, code signing/notarization and SyncDeck remain ongoing work.

Technical architecture and protocol notes are maintained in [`docs/cross-platform-protocol.md`](docs/cross-platform-protocol.md) and [`WINDOWS_BUILD_PLAN.md`](WINDOWS_BUILD_PLAN.md).
