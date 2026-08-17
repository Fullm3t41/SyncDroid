# SyncDroid-Mesh for Android

SyncDroid-Mesh is the Android 10+ peer for the local-Wi-Fi mesh. It includes authenticated discovery and pairing, folder synchronization, resumable transfers, file history, conflict review, registered-Wi-Fi power rules, and signed mesh chat.

## Build and test

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Android-specific code stays in this project. Portable protocol and synchronization behavior will move to the repository's `shared` modules.
