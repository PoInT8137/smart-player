package app.smartplayer.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.RandomAccessFile
import java.net.URI

/**
 * Lampa для ПК (lampa-desktop, Electron). Прописываем в её настройках Smart Player как внешний плеер для торрентов.
 *
 * Lampa запускает только плееры из своего списка имён (vlc, mpv, mpc-hc64…), поэтому путь — к лаунчеру
 * mpc-hc64.exe рядом с программой. Настройки Lampa лежат в localStorage (LevelDB) — пишем туда напрямую,
 * пока Lampa закрыта: те же ключи, что выставляет её собственный пункт «Выбрать плеер».
 */
object LampaDesktop {

    private val dataDir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Lampa")
    private val storage = File(dataDir, "Local Storage/leveldb")

    /** Lampa для ПК установлена и хоть раз запускалась. */
    val isInstalled: Boolean get() = File(storage, "CURRENT").isFile

    /** Лаунчер, который Lampa согласится запустить; null — программа запущена не из установленной сборки. */
    val playerPath: File?
        get() = System.getProperty("jpackage.app-path")
            ?.let { File(File(it).parentFile, "mpc-hc64.exe") }
            ?.takeIf { it.isFile }

    /** Работает ли Lampa: её база занята, пока приложение открыто. */
    fun isRunning(): Boolean = lampaProcesses().isNotEmpty() || isStorageLocked()

    /** Записывает плеер в настройки закрытой Lampa. */
    fun setExternalPlayer(player: File) {
        check(!isRunning()) { "Lampa открыта" }
        val origin = origin()
        fun key(name: String) = "_$origin".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 1) + name.toByteArray(Charsets.US_ASCII)
        LevelDbLog(storage).put(
            listOf(
                key("player_nw_path") to storageValue(player.absolutePath),
                key("player_torrent") to storageValue("other"),
            ),
        )
        AppLog.write("Lampa: внешний плеер для торрентов — ${player.absolutePath} ($origin)")
    }

    /**
     * Закрывает Lampa (как кнопкой «закрыть»), прописывает плеер и открывает её снова.
     * Возвращает false, если Lampa не закрылась сама за отведённое время.
     */
    suspend fun closeConfigureAndRestart(player: File): Boolean = withContext(Dispatchers.IO) {
        val exe = lampaProcesses().firstNotNullOfOrNull { p -> p.info().command().orElse(null) }?.let(::File)
            ?: defaultExecutable()
        if (lampaProcesses().isNotEmpty()) {
            ProcessBuilder("taskkill", "/IM", "Lampa.exe").redirectErrorStream(true).start().waitFor()
        }
        repeat(60) {
            if (!isRunning()) {
                setExternalPlayer(player)
                exe?.takeIf { it.isFile }?.let(::launch)
                return@withContext true
            }
            delay(250)
        }
        false
    }

    /**
     * Запуск Lampa отдельно от нас: её вывод — в никуда. Иначе он идёт в канал к Smart Player,
     * и когда тот закрывается, console.log в Lampa падает с «EPIPE: broken pipe».
     */
    private fun launch(exe: File) {
        ProcessBuilder(exe.absolutePath)
            .directory(exe.parentFile)
            .redirectInput(ProcessBuilder.Redirect.from(File("NUL")))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    /** Адрес, с которого Lampa открывает интерфейс: localStorage у каждого адреса свой. */
    private fun origin(): String {
        val url = runCatching {
            val config = Json.parseToJsonElement(File(dataDir, "config.json").readText()).jsonObject
            (config["lampaUrl"] as? JsonPrimitive)?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "http://lampa.mx"
        val uri = URI(if ("://" in url) url else "http://$url")
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port"
    }

    /** Строка localStorage в Chromium: 0x01 + Latin-1 или 0x00 + UTF-16LE. */
    private fun storageValue(text: String): ByteArray =
        if (text.all { it.code < 0x100 }) byteArrayOf(1) + text.toByteArray(Charsets.ISO_8859_1)
        else byteArrayOf(0) + text.toByteArray(Charsets.UTF_16LE)

    private fun lampaProcesses(): List<ProcessHandle> = ProcessHandle.allProcesses().filter { p ->
        p.info().command().map { File(it).name.equals("Lampa.exe", ignoreCase = true) }.orElse(false)
    }.toList()

    private fun isStorageLocked(): Boolean {
        val lock = File(storage, "LOCK")
        if (!lock.exists()) return false
        // Открытая база держит LOCK заблокированным; не смогли взять блокировку — значит, занята
        return runCatching {
            RandomAccessFile(lock, "rw").use { raf ->
                val taken = raf.channel.tryLock()
                taken?.release()
                taken == null
            }
        }.getOrDefault(true)
    }

    private fun defaultExecutable(): File? = listOfNotNull(
        System.getenv("ProgramFiles")?.let { File(it, "Lampa/Lampa.exe") },
        System.getenv("LOCALAPPDATA")?.let { File(it, "Programs/Lampa/Lampa.exe") },
    ).firstOrNull { it.isFile }
}
