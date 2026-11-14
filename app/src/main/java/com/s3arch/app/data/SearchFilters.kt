package com.s3arch.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Langue audio ou sous-titrage, deduite du nom du fichier. */
enum class ReleaseLanguage(
    val id: String,
    val label: String,
    /** Signification affichee a cote du filtre. */
    val meaning: String,
    private val pattern: Regex,
) {
    VFF("vff", "VFF", "Version française de France (doublage)", Regex("\\bvff\\b", RegexOption.IGNORE_CASE)),
    VF("vf", "VF", "Version française (doublage français)", Regex("\\b(vf|french)\\b", RegexOption.IGNORE_CASE)),
    VFQ("vfq", "VFQ", "Version française du Québec", Regex("\\bvfq\\b", RegexOption.IGNORE_CASE)),
    VOF("vof", "VOF", "Version originale française (sans doublage)", Regex("\\bvof\\b", RegexOption.IGNORE_CASE)),
    VO("vo", "VO", "Version originale, sans sous-titres français", Regex("\\bvo\\b", RegexOption.IGNORE_CASE)),
    VOSTFR(
        "vostfr",
        "VOSTFR",
        "Version originale sous-titrée en français",
        Regex("\\b(vostfr|vosta|subfrench)\\b", RegexOption.IGNORE_CASE),
    ),
    MULTI("multi", "MULTI", "Plusieurs pistes audio, français inclus", Regex("\\bmulti\\b", RegexOption.IGNORE_CASE)),
    TRUEFRENCH(
        "truefrench",
        "TRUEFRENCH",
        "Doublage français intégral (sans VO)",
        Regex("\\btruefrench\\b", RegexOption.IGNORE_CASE),
    ),
    SUBFRENCH(
        "subfrench",
        "SUBFRENCH",
        "Piste originale avec sous-titres français imposés",
        Regex("\\bsubfrench\\b", RegexOption.IGNORE_CASE),
    );

    fun matches(text: String): Boolean = pattern.containsMatchIn(text)
    fun matchesToken(token: String): Boolean = pattern.matches(token)

    companion object {
        fun detect(text: String): List<ReleaseLanguage> = entries.filter { it.matches(text) }
        fun anyToken(token: String): Boolean = entries.any { it.matchesToken(token) }
    }
}

/** Qualite / source video, deduite du nom du fichier. */
enum class VideoQuality(
    val id: String,
    val label: String,
    val meaning: String,
    private val pattern: Regex,
) {
    Q2160P("2160p", "2160p", "Ultra HD 4K (3840 × 2160)", Regex("\\b2160p\\b", RegexOption.IGNORE_CASE)),
    UHD("uhd", "UHD", "Ultra HD, généralement 4K", Regex("\\b(uhd|4k)\\b", RegexOption.IGNORE_CASE)),
    Q1440P("1440p", "1440p", "QHD / 2K (2560 × 1440)", Regex("\\b1440p\\b", RegexOption.IGNORE_CASE)),
    Q1080P("1080p", "1080p", "Full HD (1920 × 1080)", Regex("\\b1080p?\\b", RegexOption.IGNORE_CASE)),
    Q720P("720p", "720p", "HD (1280 × 720)", Regex("\\b720p\\b", RegexOption.IGNORE_CASE)),
    Q480P("480p", "480p / SD", "Définition standard (720 × 480)", Regex("\\b(480p|sd)\\b", RegexOption.IGNORE_CASE)),
    WEBRIP("webrip", "WEBRip", "Capture web réencodée", Regex("\\bweb[\\s-]?rip\\b", RegexOption.IGNORE_CASE)),
    WEBDL("webdl", "WEB-DL", "Web non réencodé (qualité source)", Regex("\\bweb[\\s-]?dl\\b", RegexOption.IGNORE_CASE)),
    BLURAY("bluray", "BluRay", "Rip d'un disque Blu-ray", Regex("\\b(blu[\\s-]?ray|bdrip|brrip)\\b", RegexOption.IGNORE_CASE)),
    REMUX("remux", "REMUX", "Blu-ray non réencodé (très gros)", Regex("\\bremux\\b", RegexOption.IGNORE_CASE)),
    HDRIP("hdrip", "HDRip", "Rip HD réencodé", Regex("\\bhd[\\s-]?rip\\b", RegexOption.IGNORE_CASE)),
    DVDRIP("dvdrip", "DVDRip", "Rip de DVD", Regex("\\b(dvd[\\s-]?rip|dvd[\\s-]?r)\\b", RegexOption.IGNORE_CASE)),
    HDTV("hdtv", "HDTV", "Capture de la télévision HD", Regex("\\bhdtv\\b", RegexOption.IGNORE_CASE)),
    CAM("cam", "CAM", "Filmé en salle (qualité médiocre)", Regex("\\b(cam|hdcam)\\b", RegexOption.IGNORE_CASE)),
    TS("ts", "TS", "Caméra avec son de salle", Regex("\\b(ts|telesync)\\b", RegexOption.IGNORE_CASE)),
    X265("x265", "x265 / HEVC", "Encodage HEVC (H.265)", Regex("\\b(h\\.?265|x265|hevc)\\b", RegexOption.IGNORE_CASE)),
    X264("x264", "x264 / AVC", "Encodage AVC (H.264)", Regex("\\b(h\\.?264|x264|avc)\\b", RegexOption.IGNORE_CASE)),
    AV1("av1", "AV1", "Encodage AV1", Regex("\\bav1\\b", RegexOption.IGNORE_CASE)),
    HDR("hdr", "HDR", "Haute dynamique (HDR10 / HLG)", Regex("\\bhdr(10)?\\b", RegexOption.IGNORE_CASE)),
    DOLBY_VISION(
        "dv",
        "Dolby Vision",
        "HDR Dolby Vision (DV)",
        Regex("\\b(dv|dolby[\\s.]?vision)\\b", RegexOption.IGNORE_CASE),
    );

    fun matches(text: String): Boolean = pattern.containsMatchIn(text)
    fun matchesToken(token: String): Boolean = pattern.matches(token)

    companion object {
        fun detect(text: String): List<VideoQuality> = entries.filter { it.matches(text) }
        fun anyToken(token: String): Boolean = entries.any { it.matchesToken(token) }
    }
}

/** Codec / canaux audio, deduits du nom du fichier. */
enum class AudioCodec(
    val id: String,
    val label: String,
    val meaning: String,
    private val pattern: Regex,
) {
    AAC("aac", "AAC", "Audio avancé (standard web)", Regex("\\baac\\b", RegexOption.IGNORE_CASE)),
    AC3("ac3", "AC3", "Dolby Digital (5.1 compressé)", Regex("\\bac[\\s-]?3\\b", RegexOption.IGNORE_CASE)),
    EAC3("eac3", "E-AC3", "Dolby Digital Plus", Regex("\\be[\\s-]?ac[\\s-]?3\\b", RegexOption.IGNORE_CASE)),
    DTS("dts", "DTS", "Digital Theater Systems", Regex("\\bdts\\b", RegexOption.IGNORE_CASE)),
    DTSHD("dtshd", "DTS-HD", "DTS haute définition (MA / HR)", Regex("\\bdts[\\s-]?hd\\b", RegexOption.IGNORE_CASE)),
    TRUEHD("truehd", "TrueHD", "Dolby TrueHD sans perte", Regex("\\btrue[\\s-]?hd\\b", RegexOption.IGNORE_CASE)),
    FLAC("flac", "FLAC", "Audio sans perte", Regex("\\bflac\\b", RegexOption.IGNORE_CASE)),
    MP3("mp3", "MP3", "Audio compressé MP3", Regex("\\bmp3\\b", RegexOption.IGNORE_CASE)),
    OPUS("opus", "Opus", "Audio compressé Opus", Regex("\\bopus\\b", RegexOption.IGNORE_CASE)),
    ATMOS("atmos", "Atmos", "Dolby Atmos (objet audio)", Regex("\\batmos\\b", RegexOption.IGNORE_CASE)),
    CH_5_1("5.1", "5.1", "Six canaux (5 enceintes + caisson)", Regex("\\b5[._]1\\b")),
    CH_7_1("7.1", "7.1", "Huit canaux (7 enceintes + caisson)", Regex("\\b7[._]1\\b")),
    CH_2_0("2.0", "2.0", "Stéréo deux canaux", Regex("\\b2[._]0\\b"));

    fun matches(text: String): Boolean = pattern.containsMatchIn(text)
    fun matchesToken(token: String): Boolean = pattern.matches(token)

    companion object {
        fun detect(text: String): List<AudioCodec> = entries.filter { it.matches(text) }
        fun anyToken(token: String): Boolean = entries.any { it.matchesToken(token) }
    }
}

/**
 * Filtres avances appliques a la liste de resultats.
 *
 * Un ensemble vide (ou une borne a 0) signifie « pas de filtre » : c'est ce qui
 * permet a une configuration importee d'une version anterieure de rester valable.
 */
@Serializable
data class SearchFilters(
    val languages: Set<String> = emptySet(),
    val videoQualities: Set<String> = emptySet(),
    val audioCodecs: Set<String> = emptySet(),
    /** Bornes de taille en octets ; 0 = pas de borne. */
    val minSizeBytes: Long = 0L,
    val maxSizeBytes: Long = 0L,
    /** Bornes d'annee ; 0 = pas de borne. */
    val yearMin: Int = 0,
    val yearMax: Int = 0,
    /** Nombre minimum de sources ; 0 = pas de filtre. */
    val minSeeders: Int = 0,
) {
    val isActive: Boolean
        get() = languages.isNotEmpty() || videoQualities.isNotEmpty() || audioCodecs.isNotEmpty() ||
            minSizeBytes > 0L || maxSizeBytes > 0L || yearMin > 0 || yearMax > 0 || minSeeders > 0

    fun matches(result: TorrentResult): Boolean {
        if (minSeeders > 0 && result.seeders < minSeeders) return false
        if (minSizeBytes > 0 && (result.sizeBytes <= 0L || result.sizeBytes < minSizeBytes)) return false
        if (maxSizeBytes > 0 && result.sizeBytes > maxSizeBytes) return false
        val year = MediaTitle.yearOf(result.title)
        if (yearMin > 0 && (year == null || year < yearMin)) return false
        if (yearMax > 0 && (year == null || year > yearMax)) return false
        if (languages.isNotEmpty() && ReleaseLanguage.detect(result.title).none { it.id in languages }) return false
        if (videoQualities.isNotEmpty() && VideoQuality.detect(result.title).none { it.id in videoQualities }) return false
        if (audioCodecs.isNotEmpty() && AudioCodec.detect(result.title).none { it.id in audioCodecs }) return false
        return true
    }

    /** Resume court affiche a cote du bouton de filtres. */
    fun summary(): String {
        if (!isActive) return "Aucun filtre avancé"
        val parts = buildList {
            if (languages.isNotEmpty()) add("${languages.size} langue(s)")
            if (videoQualities.isNotEmpty()) add("${videoQualities.size} qualité(s)")
            if (audioCodecs.isNotEmpty()) add("${audioCodecs.size} audio")
            if (minSizeBytes > 0L || maxSizeBytes > 0L) add("taille")
            if (yearMin > 0 || yearMax > 0) add("année")
            if (minSeeders > 0) add("≥ $minSeeders sources")
        }
        return parts.joinToString(" · ")
    }
}

/** Applique les filtres avances (aucun filtre actif = liste inchangee). */
fun List<TorrentResult>.filteredBy(filters: SearchFilters): List<TorrentResult> =
    if (!filters.isActive) {
        this
    } else {
        filter { filters.matches(it) }
    }

/** Serialisation des filtres, stockee telle quelle dans les reglages. */
object SearchFilterCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(filters: SearchFilters): String = json.encodeToString(SearchFilters.serializer(), filters)

    fun decode(raw: String?): SearchFilters {
        if (raw.isNullOrBlank()) return SearchFilters()
        return runCatching { json.decodeFromString(SearchFilters.serializer(), raw) }
            .getOrDefault(SearchFilters())
    }
}

/**
 * Normalisation des noms de fichiers pour interroger TMDB.
 *
 * `Super.Mario.Bros.le.film.2023.MULTI.VF2.720p.BluRay.AAC.2.0.x265-GROUP` devient
 * `Super Mario Bros le film` : on coupe au premier jeton qui n'appartient plus au
 * titre (annee, langue, qualite, codec, groupe).
 */
object MediaTitle {

    private val YEAR_TOKEN = Regex("^(19|20)\\d{2}$")

    /** Premiere annee plausible trouvee dans le nom. */
    fun yearOf(raw: String): Int? = raw
        .replace('.', ' ')
        .replace('_', ' ')
        .split(' ')
        .map { it.trim('-', '(', ')', '[', ']') }
        .firstOrNull { YEAR_TOKEN.matches(it) }
        ?.toIntOrNull()

    fun normalize(raw: String): String {
        val tokens = raw
            .replace('.', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
        val kept = mutableListOf<String>()
        for (token in tokens) {
            val clean = token.trim('-', '(', ')', '[', ']', ',')
            if (clean.isEmpty()) continue
            if (YEAR_TOKEN.matches(clean)) break
            if (ReleaseLanguage.anyToken(clean) ||
                VideoQuality.anyToken(clean) ||
                AudioCodec.anyToken(clean) ||
                clean.lowercase() in EXTRA_STOP
            ) {
                break
            }
            kept += clean
        }
        return kept.joinToString(" ").trim().ifBlank { raw.take(80) }
    }

    private val EXTRA_STOP = setOf(
        "vf2", "vff2", "vfi", "vost", "sub", "subs", "dubbed", "proper", "repack", "extended",
        "uncut", "directors", "imax", "complete", "integrale", "saison", "season",
        "x264", "x265", "10bit", "8bit", "multi", "truefrench", "web", "bluray", "brrip",
    )
}
