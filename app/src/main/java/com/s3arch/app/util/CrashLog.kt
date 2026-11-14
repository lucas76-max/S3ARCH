package com.s3arch.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Conserve la trace du dernier plantage pour la rendre lisible dans les journaux.
 *
 * Android ne laisse aucune trace exploitable apres un arret brutal : sans ce fichier,
 * un rapport « l'application plante » ne donne ni le type d'exception ni la ligne
 * fautive. La trace est reecrite au demarrage suivant dans [AppLog], ou elle peut etre
 * copiee depuis Reglages → Journaux.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    /** Installe le gestionnaire : ecrit la trace, puis laisse Android arreter l'app. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(appContext, thread, error) }
            // On rend la main au gestionnaire d'origine : le systeme affiche le
            // dialogue de fermeture habituel et enregistre son propre rapport.
            previous?.uncaughtException(thread, error)
        }
    }

    /** Recopie la trace du plantage precedent dans le journal, puis l'efface. */
    fun replay(context: Context) {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        runCatching { file.delete() }
        if (text.isBlank()) return
        AppLog.error("Crash", "Plantage précédent enregistré")
        text.lineSequence()
            .filter { it.isNotBlank() }
            .take(30)
            .forEach { line -> AppLog.error("Crash", line.take(300)) }
    }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date())
        val text = buildString {
            append(stamp).append(" — fil ").append(thread.name).append('\n')
            append(error.javaClass.name).append(": ").append(error.message ?: "(sans message)").append('\n')
            error.stackTrace.take(25).forEach { frame -> append("\tat ").append(frame).append('\n') }
            var cause = error.cause
            while (cause != null) {
                append("Cause : ").append(cause).append('\n')
                cause = cause.cause
            }
        }
        File(context.filesDir, FILE_NAME).writeText(text.take(8_000))
    }
}
