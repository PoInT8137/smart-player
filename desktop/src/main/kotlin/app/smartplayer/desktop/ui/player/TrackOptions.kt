package app.smartplayer.desktop.ui.player

import app.smartplayer.desktop.player.MpvEngine
import app.smartplayer.desktop.player.MpvTrack
import java.util.Locale

data class PanelOption(
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean,
    /** Заголовок группы над пунктом — когда в одной вкладке несколько настроек. */
    val header: String? = null,
    val onSelect: () -> Unit,
)

private val ru = Locale.forLanguageTag("ru")

fun audioOptions(engine: MpvEngine): List<PanelOption> {
    val audio = engine.tracks.filter { it.type == "audio" }
    return audio.mapIndexed { i, t ->
        PanelOption(
            title = trackTitle(t, "Дорожка ${i + 1}"),
            subtitle = listOfNotNull(codecName(t.codec), channelsName(t.channels)).joinToString(" · ").ifEmpty { null },
            selected = t.selected,
            onSelect = { engine.setAudio(t.id) },
        )
    }
}

fun subtitleOptions(engine: MpvEngine): List<PanelOption> {
    val subs = engine.tracks.filter { it.type == "sub" }
    val off = PanelOption("Выключены", selected = subs.none { it.selected }, onSelect = { engine.setSubtitle(null) })
    return listOf(off) + subs.mapIndexed { i, t ->
        PanelOption(
            title = trackTitle(t, "Субтитры ${i + 1}"),
            subtitle = listOfNotNull(codecName(t.codec), "форсированные".takeIf { t.forced }).joinToString(" · ").ifEmpty { null },
            selected = t.selected,
            onSelect = { engine.setSubtitle(t.id) },
        )
    }
}

/** «Русский · AC3 · 5.1» для кнопки «Звук». */
fun currentAudioSummary(engine: MpvEngine): String? = engine.tracks.firstOrNull { it.type == "audio" && it.selected }?.let { t ->
    listOfNotNull(languageName(t.lang), codecName(t.codec), channelsName(t.channels)).joinToString(" · ")
}

fun currentSubtitleSummary(engine: MpvEngine): String =
    engine.tracks.firstOrNull { it.type == "sub" && it.selected }?.let { trackTitle(it, "Вкл") } ?: "Выкл"

/** Бейджи для верхней панели: разрешение, кодек, HDR, звук. */
fun mediaBadges(engine: MpvEngine): List<String> {
    val badges = mutableListOf<String>()
    val w = maxOf(engine.videoWidth, engine.videoHeight)
    val h = minOf(engine.videoWidth, engine.videoHeight)
    when {
        w >= 3800 -> badges += "4K"
        w >= 1900 || h >= 1000 -> badges += "1080p"
        w >= 1260 || h >= 700 -> badges += "720p"
        h > 0 -> badges += "${h}p"
    }
    engine.tracks.firstOrNull { it.type == "video" && it.selected }?.codec?.let { codecName(it) }?.let { badges += it }
    engine.hdr?.let { badges += it }
    engine.tracks.firstOrNull { it.type == "audio" && it.selected }?.let { a ->
        listOfNotNull(codecName(a.codec), channelsName(a.channels)).joinToString(" ").takeIf { it.isNotBlank() }?.let { badges += it }
    }
    if (engine.fps > 0f) badges += formatFps(engine.fps) + " fps"
    return badges
}

private fun formatFps(f: Float): String =
    if (kotlin.math.abs(f - Math.round(f)) < 0.01f) Math.round(f).toString()
    else "%.3f".format(Locale.US, f).trimEnd('0').trimEnd('.')

private fun trackTitle(t: MpvTrack, fallback: String): String {
    val language = languageName(t.lang)
    val label = t.title?.takeIf { it.isNotBlank() }
    return when {
        language != null && label != null && !label.contains(language, ignoreCase = true) -> "$language — $label"
        label != null -> label
        language != null -> language
        else -> fallback
    }
}

private fun languageName(code: String?): String? = code
    ?.takeUnless { it.isBlank() || it == "und" }
    ?.let { Locale.forLanguageTag(it).getDisplayLanguage(ru).ifBlank { it } }
    ?.replaceFirstChar { it.titlecase(ru) }

/** Названия кодеков mpv → привычные. */
private fun codecName(codec: String?): String? = when (codec?.lowercase()) {
    null, "" -> null
    "ac3" -> "AC3"
    "eac3" -> "E-AC3"
    "dts" -> "DTS"
    "truehd", "mlp" -> "TrueHD"
    "aac" -> "AAC"
    "mp3", "mp2" -> "MP3"
    "opus" -> "Opus"
    "vorbis" -> "Vorbis"
    "flac" -> "FLAC"
    "pcm_s16le", "pcm_s24le", "pcm_s32le" -> "PCM"
    "h264" -> "H.264"
    "hevc" -> "HEVC"
    "av1" -> "AV1"
    "vp9" -> "VP9"
    "mpeg4" -> "MPEG-4"
    "subrip" -> "SRT"
    "ass", "ssa" -> "ASS"
    "webvtt" -> "WebVTT"
    "hdmv_pgs_subtitle" -> "PGS"
    "dvd_subtitle" -> "VobSub"
    else -> codec.uppercase()
}

private fun channelsName(count: Int?): String? = when (count) {
    null, 0 -> null
    1 -> "моно"
    2 -> "2.0"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count кан."
}

val speedValues = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

fun speedName(speed: Float): String = if (speed == 1f) "Обычная" else speed.toString().removeSuffix(".0") + "×"

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
