package com.syncdroid.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.syncdroid.app.R
import java.nio.file.Path

class AndroidUpdateFileProvider : FileProvider(R.xml.update_paths)

object AndroidUpdateInstaller {
    fun install(context: Context, installer: Path) {
        require(installer.toFile().isFile) { "The downloaded APK is unavailable" }
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            installer.toFile(),
        )
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
