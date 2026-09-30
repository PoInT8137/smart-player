package app.tvplayer.torrserver

import org.junit.Assert.assertEquals
import org.junit.Test

class TorrServerClientTest {

    @Test fun naturalOrderSortsEpisodesByNumber() {
        val files = listOf("Show S01E10.mkv", "Show S01E2.mkv", "Show S01E1.mkv", "Show S02E01.mkv")
        assertEquals(
            listOf("Show S01E1.mkv", "Show S01E2.mkv", "Show S01E10.mkv", "Show S02E01.mkv"),
            files.sortedWith(NaturalOrder),
        )
    }

    @Test fun naturalOrderHandlesFolders() {
        val files = listOf("Season 2/ep 1.mkv", "Season 1/ep 10.mkv", "Season 1/ep 9.mkv")
        assertEquals(listOf("Season 1/ep 9.mkv", "Season 1/ep 10.mkv", "Season 2/ep 1.mkv"), files.sortedWith(NaturalOrder))
    }

    @Test fun peersPlural() {
        assertEquals("1 пир", peersLabel(1))
        assertEquals("3 пира", peersLabel(3))
        assertEquals("11 пиров", peersLabel(11))
        assertEquals("22 пира", peersLabel(22))
        assertEquals("25 пиров", peersLabel(25))
        assertEquals("13 сидов", seedsLabel(13))
        assertEquals("2 сида", seedsLabel(2))
    }

    @Test fun videoFilesFilteredAndDisplayName() {
        val f = TorrentFile(1, "Sintel/Sintel.2010.1080p.mkv", 1)
        assertEquals(true, f.isVideo)
        assertEquals("Sintel 2010 1080p", f.displayName)
        assertEquals(false, TorrentFile(2, "Sintel/readme.txt", 1).isVideo)
    }
}
