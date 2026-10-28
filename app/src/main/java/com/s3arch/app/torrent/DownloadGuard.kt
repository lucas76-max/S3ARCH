package com.s3arch.app.torrent

/** Seuil a partir duquel un telechargement sur telephone demande confirmation. */
const val BIG_DOWNLOAD_BYTES = 10L * 1024L * 1024L * 1024L

/** Un fichier de plus de 10 Go declenche un avertissement explicite. */
fun requiresSizeConfirmation(sizeBytes: Long): Boolean = sizeBytes >= BIG_DOWNLOAD_BYTES

/**
 * Verifie qu'il reste assez de place **avant** de lancer.
 *
 * [requiredBytes] peut valoir 0 quand la taille est inconnue (magnet sans `xl`) :
 * dans ce cas seul un espace nul est refuse.
 */
fun spaceProblem(target: String, requiredBytes: Long, freeBytes: Long): String? {
    if (freeBytes <= 0L) return null
    if (requiredBytes <= 0L) return null
    if (requiredBytes <= freeBytes) return null
    return "Plus de place $target : ${humanSize(requiredBytes)} nécessaires, " +
        "${humanSize(freeBytes)} libres. Libérez de l'espace puis relancez."
}

/** Taille lisible, sans dependre d'Android (utilisable dans les tests). */
fun humanSize(bytes: Long): String {
    if (bytes <= 0L) return "0 o"
    val units = arrayOf("o", "Ko", "Mo", "Go", "To")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    val rounded = if (value >= 100 || index == 0) value.toLong().toString()
    else String.format(java.util.Locale.FRENCH, "%.1f", value)
    return "$rounded ${units[index]}"
}
