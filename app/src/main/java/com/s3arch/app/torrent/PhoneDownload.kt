package com.s3arch.app.torrent

import kotlinx.serialization.Serializable

/** Un telechargement lance **sur le telephone** (par opposition a la Freebox). */
@Serializable
data class PhoneDownload(
    val id: String,
    val name: String,
    val source: String,
    val kind: String = KIND_TORRENT,
    val state: String = STATE_QUEUED,
    val totalBytes: Long = 0L,
    val doneBytes: Long = 0L,
    val rateBps: Long = 0L,
    val peers: Int = 0,
    val seeders: Int = 0,
    val error: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val savePath: String = "",
    val tracker: String = "Téléphone",
) {
    val progress: Float
        get() = if (totalBytes > 0L) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val isDone: Boolean get() = state == STATE_DONE
    val isError: Boolean get() = state == STATE_ERROR
    val isPaused: Boolean get() = state == STATE_PAUSED
    val isActive: Boolean get() = state in ACTIVE

    companion object {
        const val KIND_TORRENT = "torrent"
        const val KIND_LINK = "link"
        const val STATE_QUEUED = "queued"
        const val STATE_METADATA = "metadata"
        const val STATE_CHECKING = "checking"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_DONE = "done"
        const val STATE_PAUSED = "paused"
        const val STATE_ERROR = "error"

        val ACTIVE = setOf(STATE_QUEUED, STATE_METADATA, STATE_CHECKING, STATE_DOWNLOADING)
    }
}

/** Source ajoutable : magnet, fichier .torrent ou lien direct. */
sealed class PhoneSource {
    data class Magnet(val link: MagnetLink) : PhoneSource()
    data class TorrentFile(val bytes: ByteArray, val fileName: String) : PhoneSource()
    data class Link(val url: String, val suggestedName: String?) : PhoneSource()
}

/** Convertit un lien colle en source exploitable, ou null s'il n'est pas reconnu. */
fun phoneSourceOf(text: String): PhoneSource? {
    val value = text.trim()
    if (value.isEmpty()) return null
    MagnetLink.parse(value)?.let { return PhoneSource.Magnet(it) }
    if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
        val name = value.substringBefore('?').substringAfterLast('/').takeIf { it.contains('.') }
        return PhoneSource.Link(value, name)
    }
    return null
}
