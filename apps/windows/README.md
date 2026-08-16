# SyncDows for Windows

SyncDows is the Windows peer in the SyncDroid local-Wi-Fi mesh. It uses the same Compose Desktop interface and interoperable mesh behavior as SyncTosh while storing its state beneath `%LOCALAPPDATA%\SyncDows` and integrating with File Explorer and the Windows system tray.

## Current implementation

- Equal-peer mesh creation and six-digit pairing.
- Local mDNS and UDP discovery with fingerprint-pinned TLS sessions.
- Shared pairing, session, index and transfer codecs.
- SQLite membership, folder, file-index, chat and history persistence.
- Whole-file and resumable block transfers with atomic application.
- Matching SyncTosh Sync, Folders, Devices, Chat and Settings interface.
- Folder selection/creation and Open in File Explorer.
- Conflict comparison with keep-local, keep-remote and numbered keep-both choices.
- Signed overwrite-only exception listing and Undo controls.
- Mesh-wide device renaming/removal and a signed Leave mesh flow.
- Multiple registered Wi-Fi networks for background-only power restrictions.
- Close-to-tray operation with discovery interval and duration controls.
- Self-contained EXE and MSI configuration for native Windows builds.

## Development verification

The JVM sources and shared compatibility fixtures can be compiled and tested on macOS or Linux:

```powershell
.\gradlew.bat test
```

On macOS/Linux use `./gradlew test`. Native EXE/MSI packaging must run on Windows:

```powershell
.\gradlew.bat packageExe packageMsi
```

For a clean test and both versioned installers, run:

```powershell
.\build-windows.ps1
```

The results are written to `build\release\SyncDows-0.1.0.exe` and `SyncDows-0.1.0.msi`. The repository also contains a Windows GitHub Actions workflow that runs the same compatibility tests and publishes both installers as a build artifact.

The installer bundles its Java runtime; end users do not need to install Java. Physical Windows testing remains required for Windows Firewall prompts, LAN interface selection, tray lifecycle, sleep/wake behavior and installer upgrades.
