package app.smartplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchArgsTest {

    private val url = "http://127.0.0.1:8090/stream/%D0%98%D0%BD%D1%82%D0%B5%D1%80%D0%BD%D1%8B%20001.avi?link=abc&index=1&play"

    @Test fun plainUrl() {
        val a = LaunchArgs.parse(arrayOf(url))!!
        assertEquals(url, a.target)
        assertNull(a.startPositionMs)
        assertEquals("Интерны 001", a.title)
    }

    @Test fun lampaMpcHcStyle() {
        val a = LaunchArgs.parse(arrayOf(url, "/webport", "3999", "/start", "754000", "/play", "/close", "/fullscreen"))!!
        assertEquals(url, a.target)
        assertEquals(754_000L, a.startPositionMs)
        assertEquals(3999, a.mpcWebPort)
        assertEquals(true, a.fullscreen)
    }

    @Test fun zeroStartMeansFromHistory() {
        assertNull(LaunchArgs.parse(arrayOf(url, "/start", "0"))!!.startPositionMs)
    }

    @Test fun vlcStyle() {
        val a = LaunchArgs.parse(arrayOf("--extraintf=http", "--http-port=3999", "--start-time=12.5", "--play-and-exit", url, "--fullscreen"))!!
        assertEquals(url, a.target)
        assertEquals(12_500L, a.startPositionMs)
        assertEquals(true, a.fullscreen)
    }

    @Test fun windowsFilePath() {
        val a = LaunchArgs.parse(arrayOf("D:\\Видео\\Фильм (2010).mkv"))!!
        assertEquals("Фильм (2010)", a.title)
    }

    @Test fun noTarget() = assertNull(LaunchArgs.parse(arrayOf("/play", "--fullscreen")))
}
