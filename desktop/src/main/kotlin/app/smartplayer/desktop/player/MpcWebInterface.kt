package app.smartplayer.desktop.player

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Имитация веб-интерфейса MPC-HC (`/variables.html`).
 *
 * Lampa для ПК опрашивает его каждые 5 с, чтобы знать позицию и длительность,
 * а при закрытии плеера сохраняет последнюю позицию в «Продолжить просмотр».
 * Lampa включает этот режим, если в пути к плееру есть «mpc-hc».
 */
class MpcWebInterface(port: Int) : AutoCloseable {

    @Volatile var title: String = ""
    @Volatile var path: String = ""
    @Volatile var positionMs: Long = 0
    @Volatile var durationMs: Long = 0
    @Volatile var paused: Boolean = false

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0).apply {
        createContext("/variables.html") { exchange ->
            val body = variablesHtml().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }

    fun update(progress: PlaybackProgress) {
        title = progress.item.title
        path = progress.item.url
        positionMs = progress.positionMs
        durationMs = progress.durationMs
        paused = progress.paused
    }

    private fun variablesHtml(): String {
        // state: 2 — воспроизведение, 1 — пауза (как в MPC-HC)
        val state = if (paused) 1 else 2
        return """<html><head><title>MPC-HC WebServer - Variables</title></head><body><div id="variables">
<p id="file">${escape(title)}</p>
<p id="filepath">${escape(path)}</p>
<p id="state">$state</p>
<p id="statestring">${if (paused) "Paused" else "Playing"}</p>
<p id="position">$positionMs</p>
<p id="positionstring">${clock(positionMs)}</p>
<p id="duration">$durationMs</p>
<p id="durationstring">${clock(durationMs)}</p>
<p id="volumelevel">100</p>
<p id="muted">0</p>
<p id="playbackrate">1</p>
</div></body></html>"""
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    override fun close() = server.stop(0)
}
