package com.s3arch.app.util

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("o", "Ko", "Mo", "Go", "To")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    val pattern = if (value >= 100 || index == 0) "%.0f" else "%.1f"
    return String.format(Locale.FRENCH, "$pattern %s", value, units[index])
}

fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "—"
    return "${formatBytes(bytesPerSecond)}/s"
}

fun formatEta(remainingBytes: Long, bytesPerSecond: Long): String {
    if (remainingBytes <= 0 || bytesPerSecond <= 0) return "—"
    val seconds = remainingBytes / bytesPerSecond
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> String.format(Locale.FRENCH, "%dh %02dm", hours, minutes)
        minutes > 0 -> String.format(Locale.FRENCH, "%dm %02ds", minutes, secs)
        else -> String.format(Locale.FRENCH, "%ds", secs)
    }
}

fun statusLabel(status: String): String = when (status) {
    "downloading" -> "Téléchargement"
    "queued" -> "En attente"
    "checking" -> "Vérification"
    "done" -> "Terminé"
    "seeding" -> "En partage"
    "stopped" -> "Arrêté"
    "error" -> "Erreur"
    "extracting" -> "Extraction"
    "extract_error" -> "Erreur d'extraction"
    "flush" -> "Finalisation"
    else -> status.replaceFirstChar { it.uppercase() }
}
