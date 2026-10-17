package tv.aerialtv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parsesRealCountryPlaylist() {
        val text = fixture("pk.m3u")
        val entries = text.lines().count { it.startsWith("#EXTINF") }
        val channels = M3uParser.parse(text)

        assertTrue(channels.isNotEmpty())
        // Every stream in the file ends up in exactly one channel.
        assertEquals(entries, channels.sumOf { it.streams.size })
        // Numbers run 1..n with no gaps, like a TV line up.
        assertEquals((1..channels.size).toList(), channels.map { it.number })
        // Names lose the "(720p)" and "[Geo-blocked]" noise.
        assertFalse(channels.any { "(" in it.name && "p)" in it.name })
        assertFalse(channels.any { "[" in it.name })
        assertTrue(channels.all { it.categories.isNotEmpty() })
    }

    @Test
    fun dropsStreamsThePlayerCannotOpen() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="One.pk",One
            rtsp://example.com/live
            #EXTINF:-1 tvg-id="Two.pk",Two
            https://example.com/two.m3u8
            #EXTINF:-1 tvg-id="Two.pk",Two (720p)
            rtmp://example.com/two
            #EXTINF:-1 tvg-id="Three.pk",Three
            HTTP://example.com/three.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(text)
        // A channel with only an rtsp link disappears; the rest keep just their web links.
        assertEquals(setOf("Two", "Three"), channels.map { it.name }.toSet())
        assertEquals(1, channels.first { it.name == "Two" }.streams.size)
    }

    @Test
    fun keepsHeadersFromVlcOptionsAndAttributes() {
        val channels = M3uParser.parse(fixture("ua.m3u"))
        val withAgent = channels.flatMap { it.streams }.filter { it.userAgent != null }
        assertTrue(withAgent.isNotEmpty())
        // User agents contain commas inside quotes; the name must still parse.
        assertTrue(channels.none { it.name.contains("Mozilla") })
    }

    @Test
    fun mergesFeedsOfTheSameChannel() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="Demo.pk@SD" tvg-logo="https://x/logo.png" group-title="News",Demo News (480p)
            http://a/sd.m3u8
            #EXTINF:-1 tvg-id="Demo.pk@HD" group-title="News",Demo News HD (720p) [Geo-blocked]
            #EXTVLCOPT:http-referrer=https://demo.example/
            https://a/hd.m3u8
            #EXTINF:-1 tvg-id="Other.pk@SD" http-user-agent="Mozilla/5.0 (X11, Linux)" group-title="Kids;Animation",Other
            http://b/live
        """.trimIndent()

        val channels = M3uParser.parse(text)
        assertEquals(2, channels.size)

        val demo = channels.first { it.key == "demo.pk" }
        assertEquals("Demo News", demo.name)
        assertEquals("https://x/logo.png", demo.logo)
        assertEquals(2, demo.streams.size)
        // The geo-blocked feed is tried last.
        assertEquals("http://a/sd.m3u8", demo.streams.first().url)
        assertEquals("https://demo.example/", demo.streams.last().referrer)

        val other = channels.first { it.key == "other.pk" }
        assertNotNull(other.streams.single().userAgent)
        assertEquals(listOf("kids", "animation"), other.categories)
        // News ranks before Kids in the channel numbers.
        assertEquals(1, demo.number)
    }
}
