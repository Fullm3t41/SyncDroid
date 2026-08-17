# SyncTosh macOS Design Plan

Status: Draft for review

Product: SyncTosh

Relationship: Native macOS peer for the SyncDroid mesh

## 1. Product definition

SyncTosh is a self-contained macOS application that provides the same decentralized, local-Wi-Fi file synchronization experience as SyncDroid. It is an equal mesh peer: there is no host, server, master device, or required cloud coordinator.

The first release should reproduce SyncDroid's current information architecture, visual language, settings, pairing, file management, conflict handling, chat, and sync semantics. macOS-specific additions should support the same experience without turning SyncTosh into a separate product.

### Confirmed requirements

- Product name: SyncTosh.
- One self-contained `.app`; no separately installed daemon or Java requirement.
- Join and participate in existing SyncDroid meshes.
- Same five primary sections: Sync, Folders, Devices, Chat, and Settings.
- Matching light, dark, and follow-system presentation.
- Matching expandable and collapsible cards.
- Equal-peer local Wi-Fi synchronization.
- Six-digit authenticated pairing.
- Folder filters, including extension rules such as `.sav`.
- Overwrite-only exceptions with an individual Undo action.
- File conflict review with Keep Mac, Keep Other, and Keep Both actions.
- Keep Both uses the next free `_1`, `_2`, and later suffix.
- Google Drive and OneDrive folder synchronization.
- Background discovery and synchronization using the same registered-network and schedule rules.
- Mesh group chat.

### Product language

Use:

- This device
- Trusted device
- Peer
- Mesh
- Folder
- Needs review

Avoid:

- Host
- Client
- Server
- Master
- Slave

## 2. Application shape

SyncTosh runs as one application process containing the UI, scheduler, local database, discovery service, encrypted mesh transport, folder watcher, file-transfer engine, and cloud adapters.

Closing the main window does not stop synchronization. The application remains available in the macOS menu bar. Choosing Quit stops discovery and synchronization completely.

The application bundle includes its runtime and native libraries:

```text
SyncTosh.app
├── Contents/MacOS/SyncTosh
├── Contents/Resources
├── Contents/runtime
├── Contents/Frameworks
└── Contents/Info.plist
```

No Homebrew packages, command-line tools, or external Java installation should be required.

## 3. Recommended implementation

Use Compose Multiplatform for the macOS interface and a shared Kotlin synchronization core. This provides the closest practical UI parity with the existing Android Compose application and reduces the risk of implementing the mesh rules differently on each platform.

A narrow native macOS bridge should provide:

- Keychain identity and token storage.
- Persistent security-scoped folder bookmarks.
- Bonjour browsing and advertising.
- Native notifications.
- Menu-bar integration.
- Launch-at-login registration.
- Sleep, wake, network, and removable-volume events.

The bridge remains embedded inside `SyncTosh.app`; it is not a separately installed helper.

### Proposed source layout

The recommended long-term layout is a monorepo with shared protocol code:

```text
SyncDroid/
├── shared/
│   ├── mesh-core/
│   ├── sync-engine/
│   ├── protocol/
│   └── ui-system/
├── app/                       # Android application
├── synctosh/                  # macOS application
├── mac-platform/              # Embedded native bridge
└── protocol/
```

The new local SyncTosh folder may be used for initial UI exploration. Production integration should move into the shared repository before protocol work begins, preventing two independent copies of security-sensitive synchronization code.

## 4. Shared compatibility boundary

The following behavior must be identical on Android and macOS:

- Mesh group and device identity rules.
- P-256 device keys and fingerprints.
- Signed membership events.
- Signed folder announcements.
- Signed overwrite-only exception events.
- Signed immutable chat messages.
- Version-vector comparison and merging.
- Tombstones and deletion policy.
- Index epochs and monotonic sequences.
- Metadata-received and content-applied acknowledgements.
- SHA-256 file and block validation.
- Conflict creation and resolution.
- Whole-file and resumable block transfer.
- Six-digit J-PAKE pairing transcript.
- Encrypted cloud manifests and wrapped folder keys.

Local folder paths are never transmitted. A folder such as `/Volumes/Games/Saves` is only the Mac's private mapping for a shared folder ID.

### Initial protocol strategy

1. Extract the current Android `SDMS` and `SDMB` codecs into shared Kotlin.
2. Create golden byte-for-byte fixtures for messages, signatures, IDs, and pairing transcripts.
3. Make SyncTosh interoperate with the current Android release using that shared codec.
4. Add negotiated Protobuf envelopes after basic interoperability is reliable.
5. Retain the current whole-file protocol as the compatibility fallback.

This avoids requiring a disruptive Android protocol migration before the Mac can sync.

## 5. Visual system

SyncTosh should use the same calm, minimal visual direction as SyncDroid:

- Neutral background and surface hierarchy.
- Generous whitespace.
- Rounded cards with restrained elevation.
- Green reserved for healthy and online states.
- Amber reserved for decisions requiring review.
- Red reserved for destructive actions and failures.
- Equivalent hierarchy and contrast in light and dark themes.
- Short, answer-first status text.
- Progressive disclosure through collapsing cards.

### Initial window behavior

- Default size recommendation: 1,080 × 760 points.
- Minimum size recommendation: 760 × 600 points.
- Preserve the five-tab navigation in the first parity release.
- Centre content in a readable maximum-width canvas on large displays.
- Cards grow horizontally but retain the same spacing and hierarchy as Android.
- Restore the previous window size, position, selected tab, and theme.

These dimensions are proposals and require approval before implementation.

### macOS interaction mapping

| Android interaction | SyncTosh interaction |
|---|---|
| Tap | Click |
| Long press | Long-click or right-click |
| Android back | Toolbar back button and Command–Left Bracket |
| Hold file for selection | Right-click or long-click to begin multiselect |
| Copy chat text | Long-click, right-click, or Command-C |
| Notification progress | Menu-bar progress plus Notification Centre updates |
| System folder picker fallback | Native folder permission panel |

Keyboard focus, VoiceOver labels, reduced-motion preferences, and sufficient click targets are required from the first UI milestone.

## 6. Screen specifications

### 6.1 Sync

- Answer-first state: Sync in progress, Last synced, Paused, or Needs review.
- Manual Sync now action.
- Central SyncTosh device node labelled with the Mac's device name.
- Grey text beneath the centre node: This device.
- Every trusted mesh device remains visible.
- Online devices use a green status dot.
- Offline devices use a grey status dot and show their last-online date and time.
- Connection lines render beneath nodes and do not cross where the layout can avoid it.
- Active folders list includes each folder's latest sync timestamp.
- Needs-review metric opens sequential conflict review.

### 6.2 Folders

- Expandable folder cards matching SyncDroid.
- Add a local folder using the in-app browser.
- Create a new folder after choosing its parent location.
- Configure a remotely announced folder using a new or existing local folder.
- Decline an announced folder and retain the Declined state.
- Folder-level filters, cloud switch, overwrite-only mode, and exception list.
- Open Folder shows an in-app file manager with filename, size, last edited, and last editor.
- Multiselect and delete/exclude actions.
- External volumes appear as selectable storage locations when mounted.

macOS must request access to a root folder once. SyncTosh then stores a persistent bookmark and provides its own browser beneath that approved root.

### 6.3 Devices

- Same non-crossing device mesh used on Sync.
- Start a mesh or join with six individual code boxes.
- Display remaining attempts as `X attempts remaining.`
- Five failed attempts create a fifteen-minute lockout.
- Attempts outside the rolling fifteen-minute window expire.
- Rename the current Mac.
- Remove another trusted device through a swipe-equivalent contextual action.
- Leave mesh action with confirmation.
- New trusted membership events replicate to every other peer.

### 6.4 Chat

- Same focused, single-column group chat.
- Signed immutable messages replicated through available peers.
- Chronological ordering by timestamp and stable message ID.
- Command-C and contextual Copy action.
- One-second `Copied text.` confirmation.
- Unread message notifications while the window is closed.

### 6.5 Settings

- Theme: system, light, or dark.
- Cloud sync: disabled, selected folders, or all folders.
- Registered Wi-Fi networks with multiple enabled SSIDs.
- Foreground discovery exception on any connected Wi-Fi.
- Background discovery restricted to registered Wi-Fi.
- Discovery intervals: 5, 15, and 30 minutes; 1, 6, 24, and 48 hours; one week.
- Discovery window: 30 seconds, 1 minute, 2 minutes, or 5 minutes.
- Midnight-anchored rendezvous calculation.
- Live next-three-windows preview.
- Launch at login.
- Keep running after the window closes.
- Notifications and menu-bar status preferences.
- About SyncTosh with build version and protocol version.

## 7. macOS filesystem behavior

### Folder access

- Use native user-selected read/write permission.
- Persist access using security-scoped bookmarks.
- Detect stale bookmarks and request access again without losing the mesh folder configuration.
- Clearly distinguish an unavailable external drive from a declined or unconfigured folder.

### File observation

- Use macOS filesystem events to mark folders dirty.
- Debounce rapid event bursts.
- Before hashing, require size and modification time to remain stable across a quiet period.
- Retry files that are open, incomplete, or changing.
- Never infer causality from modification time.

### Applying files

- Download into a temporary file in the destination directory.
- Hash the completed temporary file.
- Flush it before replacement.
- Atomically replace the destination where the volume supports it.
- Restore the source modification time.
- Record the received version before the watcher can misclassify it as a local edit.
- Fall back safely when a removable or network volume cannot provide atomic replacement.

### Path compatibility

- Wire paths remain Unicode NFC with `/` separators.
- Reject absolute paths and `.` or `..` traversal.
- Detect case-insensitive and normalization-insensitive APFS collisions.
- Never overwrite either colliding item automatically.
- Do not follow symbolic links during the initial release.

## 8. Discovery and background behavior

### Main window active

- Discovery remains continuously active on connected Wi-Fi.
- Registered-network restrictions are ignored while the application is in the foreground.
- Sync begins when a trusted peer is found.

### Window closed, application running

- Use the selected rendezvous schedule.
- Pause discovery away from registered Wi-Fi.
- Never stop a transfer already in progress.
- Resume when an approved network returns.
- Show status, next rendezvous, current peer, progress, and speed in the menu bar.

### Application quit

- No discovery or synchronization occurs.
- Launch at login may reopen SyncTosh into menu-bar-only mode.

### Network registration prompt

When SyncTosh sees a trusted device from the same mesh on an unregistered network, show an in-app banner:

```text
Add this Wi-Fi network?     No   Yes
```

Do not show the prompt when no trusted same-mesh peer is present.

## 9. Conflict handling

Conflicts are created from concurrent version vectors or equal vectors with different content, never solely from timestamps.

The dialog shows:

- Relative filename.
- Both device/source names.
- Last editor for each version.
- File size.
- Last modification time.
- Abbreviated SHA-256 hash.
- Remaining conflict count.

Actions:

1. Keep this Mac's version.
2. Keep the other version.
3. Keep both.
4. Review later.

Keep Both copies the Mac version to the next free suffixed name, then restores the selected peer version at the original path. The resulting resolution vector merges both parents and increments the resolving device, making the decision causally newer throughout the mesh.

## 10. Menu bar and notifications

The menu-bar item is the macOS equivalent of SyncDroid's ongoing notification.

States:

- In sync.
- Waiting for next discovery window.
- Discovering.
- Syncing with device name.
- Transfer progress and current speed.
- Paused: unregistered Wi-Fi.
- Folder needs configuration.
- Conflict needs review.
- Sync failed.

Menu actions:

- Open SyncTosh.
- Sync now.
- Show current network.
- Change discovery interval.
- Change discovery window.
- Open conflicts.
- Pause syncing.
- Quit.

Native notifications are used for action items, completed transfers, chat messages, and failures. Continuous status belongs in the menu bar rather than creating repeated notifications.

## 11. Cloud behavior

- Support Google Drive and OneDrive.
- Use the system browser for account authentication.
- Store tokens in Keychain.
- Retain folder-level and all-folders settings.
- Keep the shared remote root named `SyncDroid` for Android compatibility.
- Create one child folder per mesh folder display name.
- Encrypt manifests and file metadata before upload.
- Wrap folder keys for trusted devices.
- Treat cloud as another endpoint, not as the mesh host.

## 12. Security and privacy

- Store the device private key in Keychain and never export it.
- Require mutually authenticated, fingerprint-pinned TLS.
- Treat Bonjour records as untrusted discovery hints.
- Accept membership and exception changes only with valid trusted signatures.
- Restrict file serving to configured folder roots and indexed versions.
- Prevent path traversal and symbolic-link escape.
- Do not log pairing codes, private keys, tokens, full chat bodies, or full local paths by default.
- Clear pairing material when an invitation expires.
- Use the hardened runtime and macOS app sandbox.
- Sign and notarize public builds.

## 13. Delivery phases and exit criteria

### Phase 0 — Decisions and protocol fixtures

- Confirm deployment target, processors, bundle ID, repository structure, and signing access.
- Produce Android golden protocol fixtures.

Exit: shared compatibility suite passes independently of Android UI.

### Phase 1 — Application shell

- Self-contained macOS application bundle.
- Theme, navigation, empty states, cards, and window restoration.

Exit: every primary screen matches the approved SyncDroid visual reference.

### Phase 2 — Local persistence and file access

- SQLite schema, migrations, Keychain identity, folder bookmarks, and file manager.

Exit: folders survive restart and permission renewal safely.

### Phase 3 — Discovery and pairing

- Bonjour, pinned TLS, start/join mesh, code lockout, and membership replication.

Exit: SyncTosh and SyncDroid can pair in either direction.

### Phase 4 — Whole-file synchronization

- Scanning, stability checks, indexes, transfer, atomic application, and acknowledgements.

Exit: small files and multi-gigabyte files sync Android ↔ Mac without timestamp loops.

### Phase 5 — Conflicts and mesh features

- Conflict UI, Keep Both, exceptions, device removal, folder decline, and chat.

Exit: three-device offline and concurrent-edit scenarios converge correctly.

### Phase 6 — Resumable transfer and background lifecycle

- Block manifests, resume, menu-bar operation, schedules, Wi-Fi rules, sleep/wake recovery, and launch at login.

Exit: interrupted transfers resume and background rules behave predictably.

### Phase 7 — Cloud providers

- Google Drive, OneDrive, encryption, folder keys, and cloud conflict behavior.

Exit: Android, Mac, and cloud round trips retain hashes, authorship, and causality.

### Phase 8 — Distribution

- Security review, accessibility review, performance tests, signing, notarization, DMG, update strategy, and release documentation.

Exit: a clean Mac can install, pair, sync, update, and uninstall without external dependencies.

## 14. Required test matrix

- Start a mesh on Mac and join from Android.
- Start a mesh on Android and join from Mac.
- Add a third device and confirm membership reaches every peer.
- Sync empty, small, large, nested, and filtered folders.
- Transfer multi-gigabyte media files.
- Modify a file while it is being scanned.
- Disconnect Wi-Fi during a block transfer and resume.
- Sleep and wake the Mac during discovery and transfer.
- Change between registered and unregistered SSIDs.
- Disconnect and reconnect an external drive.
- Reopen folders from stale security-scoped bookmarks.
- Resolve two or more conflicts sequentially.
- Keep Both when `_1` and `_2` already exist.
- Delete files under propagate and overwrite-only policies.
- Test case-only and Unicode-normalization filename collisions.
- Verify editor attribution after relayed transfers.
- Upgrade the database without losing identity, membership, folder mappings, chat, or exceptions.
- Verify VoiceOver, keyboard-only use, light mode, and dark mode.

## 15. Decisions required before implementation

1. Minimum macOS version. Recommendation: macOS 13 Ventura or newer.
2. Processor support. Recommendation: Apple Silicon first; confirm whether Intel is required for the first release.
3. Bundle identifier. Proposed placeholder: `com.synctosh.app`.
4. Repository layout. Recommendation: integrate into the existing SyncDroid repository after the initial UI shell.
5. Distribution. Confirm access to an Apple Developer account for Developer ID signing and notarization.
6. Startup behavior. Confirm whether Launch at login should be opt-in or enabled during onboarding.

No unresolved choice above should be silently assumed during implementation.
