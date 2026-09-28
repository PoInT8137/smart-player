package app.tvplayer.player

import android.content.Intent
import android.net.Uri
import android.os.Parcelable

data class MediaEntry(
    val uri: Uri,
    val title: String?,
    val mimeType: String? = null,
)

data class SubtitleSource(
    val uri: Uri,
    val name: String?,
)

/**
 * Всё, что нужно для запуска воспроизведения, извлечённое из intent.
 *
 * Понимает extras популярных плееров, чтобы с нами работали приложения,
 * умеющие запускать MX Player / VLC / Vimu (в том числе Lampa):
 *  - MX: title, position (Int), headers, subs, subs.name, video_list, video_list.name
 *  - VLC: title, position (Long), from_start, subtitles_location
 *  - Vimu: forcename, startfrom
 */
data class PlaybackRequest(
    val items: List<MediaEntry>,
    val startIndex: Int,
    val startPositionMs: Long?,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleSource>,
) {
    val current: MediaEntry get() = items[startIndex]

    companion object {
        const val EXTRA_POSITION = "position"

        @Suppress("DEPRECATION")
        fun fromIntent(intent: Intent): PlaybackRequest? {
            val uri = intent.data ?: return null
            val extras = intent.extras

            val title = intent.getStringExtra("title")
                ?: intent.getStringExtra("forcename")
                ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                ?: uri.lastPathSegment

            val startPosition: Long? = when {
                intent.getBooleanExtra("from_start", false) -> 0L
                else -> (extras?.get(EXTRA_POSITION) as? Number
                    ?: extras?.get("startfrom") as? Number)?.toLong()?.takeIf { it > 0 }
            }

            val headers = intent.getStringArrayExtra("headers")
                ?.toList()
                ?.chunked(2)
                ?.filter { it.size == 2 }
                ?.associate { (k, v) -> k to v }
                .orEmpty()

            val subtitles = buildList {
                val subUris = intent.getParcelableArrayExtra("subs").toUris()
                val subNames = intent.getStringArrayExtra("subs.name")
                subUris.forEachIndexed { i, u -> add(SubtitleSource(u, subNames?.getOrNull(i))) }
                intent.getStringExtra("subtitles_location")?.let {
                    add(SubtitleSource(Uri.parse(it), null))
                }
            }

            val listUris = intent.getParcelableArrayExtra("video_list").toUris()
            val listNames = intent.getStringArrayExtra("video_list.name")

            val items: List<MediaEntry>
            val startIndex: Int
            if (listUris.size > 1) {
                items = listUris.mapIndexed { i, u ->
                    MediaEntry(u, listNames?.getOrNull(i) ?: u.lastPathSegment)
                }
                startIndex = listUris.indexOf(uri).coerceAtLeast(0)
            } else {
                items = listOf(MediaEntry(uri, title, intent.type))
                startIndex = 0
            }

            // Заголовок из "title" точнее, чем имя из плейлиста
            val fixedItems = items.toMutableList().also {
                it[startIndex] = it[startIndex].copy(title = title ?: it[startIndex].title)
            }

            return PlaybackRequest(fixedItems, startIndex, startPosition, headers, subtitles)
        }

        private fun Array<Parcelable>?.toUris(): List<Uri> =
            this?.mapNotNull { it as? Uri }.orEmpty()
    }
}
