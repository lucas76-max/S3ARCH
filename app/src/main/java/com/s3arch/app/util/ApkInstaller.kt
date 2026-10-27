package com.s3arch.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Lance l'installateur Android sur un APK telecharge.
 *
 * Android exige que l'utilisateur autorise S3ARCH a installer des applications
 * inconnues : si ce n'est pas encore fait, on ouvre directement l'ecran de reglage
 * correspondant. Renvoie un message d'erreur, ou `null` si l'ecran a ete lance.
 */
object ApkInstaller {

    fun install(context: Context, apk: File): String? {
        if (!apk.exists() || apk.length() == 0L) return "Fichier APK introuvable"
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(settings)
                "Autorisez « Installer des applications inconnues » pour S3ARCH, puis relancez la mise à jour"
            }.getOrElse { "Autorisez l'installation d'applications inconnues pour S3ARCH" }
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppLog.info("Update", "Installateur Android lancé")
            null
        } catch (e: Exception) {
            AppLog.throwable("Update", "Ouverture de l'installateur impossible", e)
            e.message ?: "Impossible de lancer l'installation"
        }
    }
}
