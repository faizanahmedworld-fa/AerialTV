package tv.aerialtv.app

/**
 * Builds the "For you" and "Most watched" rows from what the viewer told us during setup and
 * what they have actually watched. Everything runs on the device; nothing is sent anywhere.
 */
object Recommendations {

    const val FOR_YOU_SIZE = 30
    const val MOST_WATCHED_SIZE = 20

    /** Channels that fit the profile, interests and languages, best match first. */
    fun forYou(
        channels: List<Channel>,
        profile: Profile,
        interests: Set<String>,
        languages: Set<String>,
        channelLanguages: Map<String, Set<String>>,
        favorites: Set<String>,
        history: Map<String, WatchStat>,
    ): List<Channel> {
        val wanted = interests.mapNotNull(Interests::byId).flatMap { it.categories }.toSet()
        val pool = if (profile == Profile.KIDS) {
            channels.filter { ch -> ch.categories.any { it in Profile.kidSafe } }.ifEmpty { channels }
        } else {
            channels
        }

        val scored = pool.map { ch ->
            var score = 0
            ch.categories.forEachIndexed { i, cat ->
                if (cat in wanted) score += if (i == 0) 6 else 3
                if (profile == Profile.KIDS && cat in kidsFirst) score += 6
            }
            if (channelLanguages[ch.key].orEmpty().any { it in languages }) score += 5
            if (ch.key in favorites) score += 12
            history[ch.key]?.let { score += minOf(it.count, 8) + minOf((it.seconds / 300).toInt(), 6) }
            if (!ch.logo.isNullOrBlank()) score += 1
            if (ch.streams.all { it.geoBlocked }) score -= 4
            ch to score
        }

        val positive = scored.filter { it.second > 0 }
        // Nothing ticked yet and nothing watched: fall back to the start of the line up, which
        // the parser already sorts general and news first.
        if (positive.isEmpty()) return pool.take(FOR_YOU_SIZE)
        return positive
            .sortedWith(compareByDescending<Pair<Channel, Int>> { it.second }.thenBy { it.first.number })
            .map { it.first }
            .take(FOR_YOU_SIZE)
    }

    /** What this device watches most, by time with a nudge for how often. */
    fun mostWatched(channels: List<Channel>, history: Map<String, WatchStat>): List<Channel> {
        if (history.isEmpty()) return emptyList()
        val byKey = channels.associateBy { it.key }
        return history.entries
            .filter { it.value.seconds >= 60 || it.value.count >= 2 }
            .sortedByDescending { it.value.seconds + it.value.count * 120 }
            .mapNotNull { byKey[it.key] }
            .take(MOST_WATCHED_SIZE)
    }

    /** Name, number or category search. "sport" finds every sports channel, "7" channel 7. */
    fun search(channels: List<Channel>, query: String): List<Channel> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return channels
        val number = q.toIntOrNull()
        val words = q.split(' ').filter { it.isNotBlank() }
        return channels.filter { ch ->
            (number != null && ch.number == number) ||
                words.all { w -> ch.name.lowercase().contains(w) || ch.categories.any { it.contains(w) } }
        }.sortedWith(
            compareBy<Channel>(
                { if (number != null && it.number == number) 0 else 1 },
                { if (it.name.lowercase().startsWith(q)) 0 else 1 },
                { it.number },
            ),
        )
    }

    private val kidsFirst = setOf("kids", "animation", "family")
}
