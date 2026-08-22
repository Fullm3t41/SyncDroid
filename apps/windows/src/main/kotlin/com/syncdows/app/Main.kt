package com.syncdows.app

/**
 * Keep the packaged launcher free of Compose references so the background
 * worker does not load Skia, Compose, or the UI graphics heap. The full UI is
 * launched as a separate process only while its window is open.
 */
fun main(args: Array<String>) {
    if (UI_ARGUMENT in args) {
        runSyncDowsUi(args)
    } else {
        SyncDowsWorker(args).run()
    }
}

internal const val UI_ARGUMENT = "--ui"
