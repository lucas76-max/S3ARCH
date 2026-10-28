package com.s3arch.app.data

/**
 * Import des cles API depuis un fichier texte fourni par l'utilisateur.
 *
 * Format attendu, une ligne par tracker :
 *
 * ```
 * V3X = ma-cle
 * C411 = ma-cle
 * YGG_REBORN = ma-passkey
 * ASTRATORRENT = ma-cle
 * ZXX_CLUB = ma-cle
 * ```
 *
 * Le separateur `=` peut etre remplace par `:`, les lignes vides et celles
 * commencant par `#` sont ignorees, et les noms sont insensibles a la casse.
 * Un nom inconnu est ignore plutot que de faire echouer tout l'import.
 */
object KeyImport {

    /** Noms acceptes dans le fichier, y compris les variantes courantes. */
    private val NAMES: Map<String, Tracker> = buildMap {
        Tracker.entries.forEach { tracker ->
            put(tracker.id, tracker)
            put(tracker.label, tracker)
        }
        put("ASTRA", Tracker.ASTRATORRENT)
        put("ASTRA_TORRENT", Tracker.ASTRATORRENT)
        put("YGG_REBORN", Tracker.YGG_REBORN)
        put("YGGREBORN", Tracker.YGG_REBORN)
        put("YGG", Tracker.YGG_REBORN)
        put("ZXX", Tracker.ZXX_CLUB)
        put("ZXX_CLUB", Tracker.ZXX_CLUB)
        put("ZXXCLUB", Tracker.ZXX_CLUB)
        put("C411_ORG", Tracker.C411)
        put("TR4KER_NET", Tracker.TR4KER)
    }.mapKeys { (name, _) -> normalize(name) }

    /** Une ligne reconnue du fichier, dans l'ordre de lecture. */
    data class Entry(val tracker: Tracker, val key: String)

    fun parse(text: String): List<Entry> = text
        .lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val separator = line.indexOfFirst { it == '=' || it == ':' }
            if (separator <= 0) return@mapNotNull null
            val name = normalize(line.substring(0, separator))
            val value = line.substring(separator + 1).trim().trim('"', '\'', ' ')
            val tracker = NAMES[name] ?: return@mapNotNull null
            if (value.isBlank()) null else Entry(tracker, value)
        }
        .distinctBy { it.tracker }
        .toList()

    /** Map exploitable directement par le ViewModel. */
    fun asMap(text: String): Map<Tracker, String> =
        parse(text).associate { it.tracker to it.key }

    private fun normalize(raw: String): String =
        raw.trim().uppercase().replace(' ', '_').replace('-', '_')
}
