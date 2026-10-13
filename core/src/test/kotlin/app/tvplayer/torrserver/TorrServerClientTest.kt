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

    @Test fun parsesLampaStreamLink() {
        val link = TorrServerClient.parseLink("http://192.168.1.5:8090/stream/Film%20(2010).mkv?link=ABCDEF0123456789ABCDEF0123456789ABCDEF01&index=1&play")
        assertEquals(TorrentLink("http://192.168.1.5:8090", "abcdef0123456789abcdef0123456789abcdef01"), link)
    }

    @Test fun parsesMagnetInLinkAndPlayFormat() {
        val magnet = "magnet:?xt=urn:btih:ABCDEF0123456789ABCDEF0123456789ABCDEF01&dn=x"
        val link = TorrServerClient.parseLink("http://127.0.0.1:8090/stream/x.mkv?link=" + java.net.URLEncoder.encode(magnet, Charsets.UTF_8))
        assertEquals("abcdef0123456789abcdef0123456789abcdef01", link?.hash)
        assertEquals("ff00", TorrServerClient.parseLink("http://127.0.0.1:8090/play/FF00/1")?.hash)
        assertEquals(null, TorrServerClient.parseLink("http://example.com/video.mkv"))
    }

    @Test fun parsesTorrentJson() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"title":"Film","hash":"aa","poster":"http://p/x.jpg","torrent_size":100,"stat":3,
               "download_speed":2048.5,"active_peers":7,"file_stats":[{"id":2,"path":"a/E10.mkv","length":5},
               {"id":1,"path":"a/E2.mkv","length":5},{"id":3,"path":"a/info.txt","length":1}]}"""
        ) as kotlinx.serialization.json.JsonObject
        val t = TorrentInfo.fromJson(json)
        assertEquals("Film", t.title)
        assertEquals(7, t.activePeers)
        assertEquals(listOf("E2", "E10"), t.videoFiles.map { it.displayName.substringAfterLast(' ') })
    }
}
