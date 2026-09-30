package app.tvplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.tvplayer.data.HistoryEntry
import app.tvplayer.data.PlaybackHistory
import app.tvplayer.data.PlayerSettings
import app.tvplayer.torrserver.TorrServerClient
import app.tvplayer.torrserver.TorrentFile
import app.tvplayer.torrserver.TorrentInfo
import app.tvplayer.ui.home.TorrServerState
import app.tvplayer.demo.DemoContent
import app.tvplayer.demo.DemoItem
import app.tvplayer.player.PlayerActivity
import app.tvplayer.player.ui.formatTime
import app.tvplayer.ui.TVPlayerTheme
import app.tvplayer.ui.home.HomeScreen

class MainActivity : ComponentActivity() {

    private lateinit var history: PlaybackHistory
    private lateinit var settings: PlayerSettings
    private var continueWatching by mutableStateOf<List<HistoryEntry>>(emptyList())
    private var torrServer by mutableStateOf<TorrServerState>(TorrServerState.Loading)
    private var resultMessage by mutableStateOf<String?>(null)

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        // Сохраняем доступ, чтобы потом можно было продолжить просмотр из истории
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        play(uri, null)
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { play(it, null) }
    }

    /** Запуск «как из Lampa»: получаем результат так же, как его получила бы Lampa. */
    private val playForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::onPlayerResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = PlaybackHistory(this)
        settings = PlayerSettings(this)
        setContent {
            TVPlayerTheme {
                HomeScreen(
                    continueWatching = continueWatching,
                    demos = DemoContent.items,
                    resultMessage = resultMessage,
                    onDismissResult = { resultMessage = null },
                    onPlayHistory = { play(Uri.parse(it.uri), it.title) },
                    onRemoveHistory = {
                        history.remove(it.uri)
                        refresh()
                    },
                    onPlayDemo = ::playDemo,
                    onOpenFile = ::pickFile,
                    torrServer = torrServer,
                    loadTorrent = { hash -> TorrServerClient(settings.torrServerUrl).get(hash) },
                    onPlayTorrentFile = ::playTorrentFile,
                    onRetryTorrServer = ::refreshTorrServer,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        continueWatching = history.continueWatching()
        refreshTorrServer()
    }

    private fun refreshTorrServer() {
        val client = TorrServerClient(settings.torrServerUrl)
        lifecycleScope.launch {
            torrServer = runCatching { client.list() }
                .fold(
                    onSuccess = { TorrServerState.Ready(client.baseUrl, it) },
                    onFailure = { TorrServerState.Unavailable(client.baseUrl) },
                )
        }
    }

    /** Запуск файла из раздачи: все видео раздачи уходят в плеер плейлистом, как это делает Lampa. */
    private fun playTorrentFile(torrent: TorrentInfo, file: TorrentFile) {
        val client = TorrServerClient(settings.torrServerUrl)
        val videos = torrent.videoFiles
        val single = videos.size == 1
        val intent = Intent(Intent.ACTION_VIEW)
            .setClass(this, PlayerActivity::class.java)
            .setDataAndType(Uri.parse(client.streamUrl(torrent.hash, file)), "video/*")
            .putExtra("title", if (single) torrent.title else file.displayName)
        if (!single) {
            val uris: Array<Parcelable> = videos.map { Uri.parse(client.streamUrl(torrent.hash, it)) }.toTypedArray()
            intent.putExtra("video_list", uris)
            intent.putExtra("video_list.name", videos.map { it.displayName }.toTypedArray())
        }
        startActivity(intent)
    }

    private fun pickFile() {
        try {
            openDocument.launch(arrayOf("video/*", "audio/*"))
        } catch (_: ActivityNotFoundException) {
            try {
                getContent.launch("video/*")
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    "На устройстве нет выбора файлов. Встроенный файловый браузер появится в следующих версиях.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun play(uri: Uri, title: String?) {
        val intent = Intent(this, PlayerActivity::class.java)
            .setData(uri)
            .putExtra("title", title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    /** Формирует intent в том же формате (MX Player), в каком его отправляет Lampa. */
    private fun playDemo(demo: DemoItem) {
        val (firstTitle, firstUrl) = demo.episodes.first()
        val intent = Intent(Intent.ACTION_VIEW)
            .setClass(this, PlayerActivity::class.java)
            .setDataAndType(Uri.parse(firstUrl), "video/*")
            .putExtra("title", firstTitle)
            .putExtra("return_result", true)
        if (demo.episodes.size > 1) {
            val uris: Array<Parcelable> = demo.episodes.map { Uri.parse(it.second) }.toTypedArray()
            intent.putExtra("video_list", uris)
            intent.putExtra("video_list.name", demo.episodes.map { it.first }.toTypedArray())
        }
        demo.startPositionMs?.let { intent.putExtra("position", it.toInt()) }
        playForResult.launch(intent)
    }

    private fun onPlayerResult(result: ActivityResult) {
        val data = result.data ?: return
        val position = data.getIntExtra("position", -1)
        val duration = data.getIntExtra("duration", -1)
        if (position < 0) return
        val how = if (data.getStringExtra("end_by") == "playback_completion") "досмотрено до конца" else "остановлено"
        resultMessage = "Плеер вернул в Lampa: ${formatTime(position.toLong())} из ${formatTime(duration.toLong())} · $how"
    }
}
