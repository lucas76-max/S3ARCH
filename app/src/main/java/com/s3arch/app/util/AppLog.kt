package com.s3arch.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    fun format(): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(timestamp))
        return "$time  ${level.name.padEnd(5)}  $tag  $message"
    }
}

/**
 * Journal en memoire (500 dernieres entrees) affiche dans les reglages.
 *
 * Toutes les valeurs sensibles (cle API, passkey, token) sont masquees par [redact]
 * avant d'etre enregistrees : le journal peut donc etre copie sans risque.
 */
object AppLog {

    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun info(tag: String, message: String) = add(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = add(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = add(LogLevel.ERROR, tag, message)

    /**
     * Journalise une exception avec son type et les premieres frames de la pile.
     *
     * Une exception Android comme [android.os.NetworkOnMainThreadException] n'a
     * **aucun message** : sans le type et la pile, le journal affichait un simple
     * « Lecture impossible » impossible a diagnostiquer.
     */
    fun throwable(tag: String, context: String, error: Throwable) {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: "(sans message)"
        error(tag, "$context : ${error.javaClass.simpleName}: $detail")
        val frames = error.stackTrace
            .take(4)
            .joinToString(" <-") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        if (frames.isNotBlank()) error(tag, frames)
    }

    @Synchronized
    private fun add(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = redact(message).take(1_500),
        )
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }

    fun asText(): String = _entries.value.joinToString("\n") { it.format() }

    private val SECRET_PARAM = Regex(
        "(?i)(apikey|api_key|passkey|pass_key|token|auth|session_token|password|pwd)=([^&\\s\"]+)",
    )

    /** Masque les parametres sensibles d'une URL ou d'un message. */
    fun redact(value: String): String = SECRET_PARAM.replace(value) { match ->
        "${match.groupValues[1]}=***"
    }
}
