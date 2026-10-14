package app.smartplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.smartplayer.desktop.data.AppLog
import app.smartplayer.desktop.data.HistoryEntry
import app.smartplayer.desktop.data.LampaDesktop
import app.smartplayer.desktop.data.PlaybackHistory
import app.smartplayer.desktop.data.Settings
import app.smartplayer.desktop.player.PlayerWindow
import app.smartplayer.desktop.player.PlayItem
import app.smartplayer.desktop.player.PlaybackProgress
import app.tvplayer.torrserver.TorrServerClient
import app.tvplayer.torrserver.TorrentFile
import app.tvplayer.torrserver.TorrentInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Подключение к Lampa для ПК: чтобы фильмы из неё открывались в Smart Player. */
sealed interface LampaLink {
    /** Lampa не найдена или программа запущена не из установленной сборки — нечего настраивать. */
    data object Unavailable : LampaLink
    data object Linked : LampaLink
    /** Lampa открыта — менять её настройки можно только закрыв её. */
    data object NeedsClose : LampaLink
    data object Working : LampaLink
    data class Failed(val reason: String) : LampaLink
}

sealed interface TorrServerState {
    data object Loading : TorrServerState
    data class Unavailable(val url: String) : TorrServerState
    data class Ready(val url: String, val torrents: List<TorrentInfo>) : TorrServerState
}

/** Состояние и действия главного окна. */
class AppModel(private val scope: CoroutineScope) {

    val history = PlaybackHistory()
    val settings = Settings()
    private val player = PlayerWindow()

    var continueWatching by mutableStateOf(history.continueWatching())
        private set
    var torrServer by mutableStateOf<TorrServerState>(TorrServerState.Loading)
        private set
    /** Название того, что сейчас идёт в окне mpv. */
    var nowPlaying by mutableStateOf<String?>(null)
        private set
    /** Короткое сообщение внизу экрана. */
    var message by mutableStateOf<String?>(null)

    var lampa by mutableStateOf<LampaLink>(LampaLink.Unavailable)
        private set

    val client: TorrServerClient get() = TorrServerClient(settings.torrServerUrl)

    /**
     * При запуске: если Lampa ещё не настроена на этот Smart Player — настраиваем сами
     * (если она закрыта) или предлагаем закрыть её кнопкой.
     */
    fun checkLampa() {
        val player = LampaDesktop.playerPath
        if (!LampaDesktop.isInstalled || player == null) {
            lampa = LampaLink.Unavailable
            return
        }
        if (settings.lampaLinkedPath.equals(player.absolutePath, ignoreCase = true)) {
            lampa = LampaLink.Linked
            return
        }
        scope.launch {
            lampa = if (withContext(Dispatchers.IO) { LampaDesktop.isRunning() }) LampaLink.NeedsClose else linkLampa(player)
        }
    }

    /** Кнопка «Подключить к Lampa»: при необходимости закрывает Lampa и открывает её снова. */
    fun connectLampa() {
        val player = LampaDesktop.playerPath ?: return
        lampa = LampaLink.Working
        scope.launch {
            lampa = runCatching {
                if (withContext(Dispatchers.IO) { LampaDesktop.isRunning() }) {
                    if (!LampaDesktop.closeConfigureAndRestart(player)) return@runCatching LampaLink.Failed("Lampa не закрылась — закройте её сами и нажмите ещё раз")
                    settings.lampaLinkedPath = player.absolutePath
                    LampaLink.Linked
                } else linkLampa(player)
            }.getOrElse { LampaLink.Failed(it.message ?: "не удалось изменить настройки Lampa") }
            if (lampa == LampaLink.Linked) message = "Готово: торренты из Lampa теперь открываются в Smart Player"
        }
    }

    private suspend fun linkLampa(player: File): LampaLink = runCatching {
        withContext(Dispatchers.IO) { LampaDesktop.setExternalPlayer(player) }
        settings.lampaLinkedPath = player.absolutePath
        message = "Lampa настроена: торренты из неё открываются в Smart Player"
        LampaLink.Linked
    }.getOrElse {
        AppLog.write("Lampa: не удалось настроить", it)
        LampaLink.Failed(it.message ?: "не удалось изменить настройки Lampa")
    }

    fun refresh() {
        continueWatching = history.continueWatching()
        refreshTorrServer()
    }

    fun refreshTorrServer() {
        val c = client
        torrServer = TorrServerState.Loading
        scope.launch {
            torrServer = runCatching { c.list() }.fold(
                onSuccess = { TorrServerState.Ready(c.baseUrl, it) },
                onFailure = { TorrServerState.Unavailable(c.baseUrl) },
            )
        }
    }

    fun removeFromHistory(entry: HistoryEntry) {
        history.remove(entry.uri)
        continueWatching = history.continueWatching()
    }

    fun play(items: List<PlayItem>, startIndex: Int = 0, startPositionMs: Long? = null) {
        if (!player.isAvailable) {
            message = "Не найден видеодвижок mpv рядом с программой"
            return
        }
        if (nowPlaying != null) {
            message = "Плеер уже открыт — закройте его окно, чтобы начать другое видео"
            return
        }
        val first = items[startIndex]
        // Если позицию не передали — продолжаем с места, где остановились
        val resume = startPositionMs ?: history.get(first.url)?.resumePositionMs
        nowPlaying = first.title
        scope.launch {
            val result = player.play(
                items = items,
                startIndex = startIndex,
                startPositionMs = resume,
                onProgress = ::saveProgress,
                onItemFinished = { history.remove(it.url) },
            )
            result?.let { if (it.completed) history.remove(it.item.url) else saveProgress(it) }
            nowPlaying = null
            refresh()
        }
    }

    fun playHistory(entry: HistoryEntry) = play(listOf(PlayItem(entry.uri, entry.title)))

    fun playFile(file: File) = play(listOf(PlayItem(file.absolutePath, file.nameWithoutExtension)))

    /** Все видео раздачи уходят в плеер плейлистом, как это делает Lampa. */
    fun playTorrentFile(torrent: TorrentInfo, file: TorrentFile) {
        val c = client
        val videos = torrent.videoFiles
        val single = videos.size == 1
        val items = videos.map { PlayItem(c.streamUrl(torrent.hash, it), if (single) torrent.title else it.displayName) }
        play(items, videos.indexOfFirst { it.id == file.id }.coerceAtLeast(0))
    }

    /**
     * Ссылка из буфера: magnet/хеш — добавляем в TorrServer и возвращаем раздачу,
     * http(s) или путь к файлу — сразу воспроизводим.
     */
    suspend fun openLink(text: String): TorrentInfo? {
        val link = text.trim()
        return when {
            link.startsWith("magnet:", ignoreCase = true) || Regex("^[0-9a-fA-F]{40}$").matches(link) -> {
                val torrent = client.add(link)
                refreshTorrServer()
                torrent
            }
            link.startsWith("http://") || link.startsWith("https://") -> {
                play(listOf(PlayItem(link, link.substringAfterLast('/').substringBefore('?').ifBlank { link })))
                null
            }
            File(link).isFile -> {
                playFile(File(link))
                null
            }
            else -> error("Не похоже на ссылку: вставьте magnet, адрес http(s) или путь к файлу")
        }
    }

    private fun saveProgress(p: PlaybackProgress) {
        history.save(HistoryEntry(p.item.url, p.item.title, p.positionMs, p.durationMs, System.currentTimeMillis()))
    }
}
