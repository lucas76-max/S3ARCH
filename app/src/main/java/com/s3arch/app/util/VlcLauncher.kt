package com.s3arch.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Lance la lecture d'un flux dans VLC (ou dans le lecteur par defaut a defaut). */
object VlcLauncher {

    private const val VLC_PACKAGE = "org.videolan.vlc"

    fun play(context: Context, url: String, title: String): Boolean {
        val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(VLC_PACKAGE)
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(vlcIntent)
            return true
        } catch (e: ActivityNotFoundException) {
            // VLC absent : on propose les lecteurs capables de lire le flux.
            val generic = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(Intent.createChooser(generic, "Lire avec").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (ex: ActivityNotFoundException) {
                false
            }
        }
    }
}
