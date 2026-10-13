package app.tvplayer.torrserver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class TorrentFile(val id: Int, val path: String, val length: Long) {
    val name: String get() = path.substringAfterLast('/')
    val isVideo: Boolean get() = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    /** Имя без расширения и лишних точек — для заголовка серии. */
    val displayName: String
        get() = name.substringBeforeLast('.').replace('.', ' ').replace('_', ' ').trim()

    private companion object {
        val VIDEO_EXTENSIONS = setOf(
            "mkv", "mp4", "m4v", "avi", "mov", "ts", "m2ts", "mts", "webm", "wmv", "mpg", "mpeg", "flv", "vob",
        )
    }
}

/** Состояние торрента в TorrServer (MatriX API, /torrents). */
data class TorrentInfo(
    val hash: String,
    val title: String,
    val poster: String?,
    val size: Long,
    /** 0 добавлен, 1 получение info, 2 предзагрузка, 3 работает, 4 закрыт, 5 в базе. */
    val stat: Int,
    val statString: String,
    /** Байт/с. */
    val downloadSpeed: Double,
    val activePeers: Int,
    val totalPeers: Int,
    val seeders: Int,
    val preloadedBytes: Long,
    val preloadSize: Long,
    val files: List<TorrentFile>,
) {
    val isPreloading: Boolean get() = stat == STAT_PRELOAD && preloadSize > 0
    val preloadProgress: Float get() = if (preloadSize > 0) (preloadedBytes.toFloat() / preloadSize).coerceIn(0f, 1f) else 0f
    val videoFiles: List<TorrentFile> get() = files.filter { it.isVideo }.sortedWith(compareBy(NaturalOrder) { it.path })

    companion object {
        const val STAT_PRELOAD = 2

        fun fromJson(o: JsonObject) = TorrentInfo(
            hash = o.str("hash"),
            title = o.str("title").ifBlank { o.str("name") },
            poster = o.str("poster").takeIf { it.startsWith("http") },
            size = o.long("torrent_size"),
            stat = o.long("stat", -1).toInt(),
            statString = o.str("stat_string"),
            downloadSpeed = (o["download_speed"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            activePeers = o.long("active_peers").toInt(),
            totalPeers = o.long("total_peers").toInt(),
            seeders = o.long("connected_seeders").toInt(),
            preloadedBytes = o.long("preloaded_bytes"),
            preloadSize = o.long("preload_size"),
            files = (o["file_stats"] as? kotlinx.serialization.json.JsonArray)?.map { el ->
                val f = el.jsonObject
                TorrentFile(f.long("id").toInt(), f.str("path"), f.long("length"))
            }.orEmpty(),
        )

        private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.long(key: String, default: Long = 0): Long =
            (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: default
    }
}

/** Ссылка на поток TorrServer, распознанная из URL: адрес сервера + хеш раздачи. */
data class TorrentLink(val baseUrl: String, val hash: String)

/**
 * Клиент TorrServer. Все запросы короткие (таймаут 4 с) — сервер обычно
 * на той же приставке или в локальной сети.
 */
class TorrServerClient(baseUrl: String) {

    val baseUrl: String = baseUrl.trimEnd('/')

    /** Версия сервера — заодно проверка, что он доступен. */
    suspend fun echo(): String = request("GET", "/echo", null).trim()

    suspend fun list(): List<TorrentInfo> =
        parse(request("POST", "/torrents", action("list"))).jsonArray.map { TorrentInfo.fromJson(it.jsonObject) }

    /** Подробности с файлами. Внимание: «будит» торрент — TorrServer начнёт подключаться к пирам. */
    suspend fun get(hash: String): TorrentInfo =
        TorrentInfo.fromJson(parse(request("POST", "/torrents", action("get", hash))).jsonObject)

    private fun action(name: String, hash: String? = null): String = buildJsonObject {
        put("action", name)
        if (hash != null) put("hash", hash)
    }.toString()

    private fun parse(text: String): JsonElement = Json.parseToJsonElement(text)

    /** Ссылка на файл в формате, который использует Lampa, — чтобы совпадала история просмотра. */
    fun streamUrl(hash: String, file: TorrentFile): String =
        "$baseUrl/stream/${URLEncoder.encode(file.name, Charsets.UTF_8).replace("+", "%20")}?link=$hash&index=${file.id}&play"

    private suspend fun request(method: String, path: String, body: String?): String = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            if (conn.responseCode !in 200..299) error("TorrServer HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8090"
        private const val TIMEOUT_MS = 4_000

        /**
         * Распознаёт ссылки TorrServer:
         *  - /stream/<имя>?link=<hash>&index=N (Lampa)
         *  - /play/<hash>/<index>
         */
        fun parseLink(url: String): TorrentLink? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme != "http" && uri.scheme != "https") return null
            val base = "${uri.scheme}://${uri.rawAuthority}"
            val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
            val link = queryParam(uri.rawQuery, "link")
            return when {
                segments.firstOrNull() == "stream" && !link.isNullOrBlank() -> TorrentLink(base, hashFromLink(link))
                segments.firstOrNull() == "play" && segments.size >= 2 -> TorrentLink(base, segments[1].lowercase())
                else -> null
            }
        }

        private fun queryParam(rawQuery: String?, name: String): String? =
            rawQuery?.split('&')
                ?.map { it.split('=', limit = 2) }
                ?.firstOrNull { it[0] == name }
                ?.getOrNull(1)
                ?.let { URLDecoder.decode(it, Charsets.UTF_8) }

        /** link может быть хешем или magnet-ссылкой. */
        private fun hashFromLink(link: String): String =
            Regex("btih:([0-9a-fA-F]{40})").find(link)?.groupValues?.get(1)?.lowercase() ?: link.lowercase()
    }
}

/** Естественная сортировка: «Серия 2» раньше «Серия 10». */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 }
                    ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else {
                p.compareTo(q)
            }
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}

/** 2 500 000 → «2.4 МБ/с». */
fun formatSpeed(bytesPerSecond: Double): String = when {
    bytesPerSecond >= 1024 * 1024 -> "%.1f МБ/с".format(bytesPerSecond / (1024 * 1024))
    bytesPerSecond >= 1024 -> "%.0f КБ/с".format(bytesPerSecond / 1024)
    else -> "0 КБ/с"
}

/** 4 600 000 000 → «4.3 ГБ». */
fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f МБ".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} КБ"
}

/** «12 пиров», «1 пир», «3 пира». */
fun peersLabel(n: Int): String = plural(n, "пир", "пира", "пиров")

/** «13 сидов», «1 сид», «2 сида». */
fun seedsLabel(n: Int): String = plural(n, "сид", "сида", "сидов")

private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}
