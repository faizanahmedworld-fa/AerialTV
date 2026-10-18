package tv.aerialtv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationsTest {

    private fun channel(key: String, vararg categories: String, number: Int, logo: String? = "x") = Channel(
        key = key, name = key.replaceFirstChar { it.uppercase() }, logo = logo, categories = categories.toList(),
        streams = listOf(StreamSource("https://e.x/$key.m3u8", null, null, 720, false)), number = number,
    )

    private val lineUp = listOf(
        channel("ptv", "general", number = 1),
        channel("geo news", "news", number = 2),
        channel("ary news", "news", number = 3),
        channel("ptv sports", "sports", number = 4),
        channel("cartoon kids", "kids", "animation", number = 5),
        channel("hum tv", "entertainment", number = 6),
        channel("quran tv", "religious", number = 7),
    )

    @Test
    fun interestsPutMatchingChannelsFirst() {
        val list = Recommendations.forYou(lineUp, Profile.FAMILY, setOf("sports", "news"), emptySet(), emptyMap(), emptySet(), emptyMap())
        assertEquals(setOf("geo news", "ary news", "ptv sports"), list.take(3).map { it.key }.toSet())
        // Channels with no match still follow, so the row never looks empty.
        assertTrue(list.size >= 3)
    }

    @Test
    fun kidsProfileOnlyShowsKidSafeChannels() {
        val list = Recommendations.forYou(lineUp, Profile.KIDS, emptySet(), emptySet(), emptyMap(), emptySet(), emptyMap())
        assertEquals(listOf("cartoon kids"), list.map { it.key })
    }

    @Test
    fun favouritesLanguageAndHistoryLiftAChannel() {
        val history = mapOf("hum tv" to WatchStat(count = 5, seconds = 3600, last = 0))
        val langs = mapOf("quran tv" to setOf("urd"))
        val list = Recommendations.forYou(lineUp, Profile.ADULTS, emptySet(), setOf("urd"), langs, setOf("ptv sports"), history)
        assertEquals(listOf("ptv sports", "hum tv", "quran tv"), list.take(3).map { it.key })
    }

    @Test
    fun mostWatchedIgnoresChannelsOnlyZappedPast() {
        val history = mapOf(
            "ptv" to WatchStat(count = 1, seconds = 5, last = 0),
            "geo news" to WatchStat(count = 3, seconds = 900, last = 0),
        )
        assertEquals(listOf("geo news"), Recommendations.mostWatched(lineUp, history).map { it.key })
    }

    @Test
    fun searchMatchesNameNumberAndCategory() {
        assertEquals(listOf("ptv sports"), Recommendations.search(lineUp, "sport").map { it.key })
        assertEquals(listOf("hum tv"), Recommendations.search(lineUp, "6").map { it.key })
        assertEquals(setOf("geo news", "ary news"), Recommendations.search(lineUp, "news").map { it.key }.toSet())
        assertEquals(listOf("ptv", "ptv sports"), Recommendations.search(lineUp, "ptv").map { it.key })
        assertTrue(Recommendations.search(lineUp, "zzz").isEmpty())
    }
}
