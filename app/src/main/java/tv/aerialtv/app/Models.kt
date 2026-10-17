package tv.aerialtv.app

/** One playable URL for a channel, with the headers some streams insist on. */
data class StreamSource(
    val url: String,
    val userAgent: String?,
    val referrer: String?,
    val quality: Int,
    val geoBlocked: Boolean,
)

/** A channel as the viewer sees it. Several iptv-org entries can merge into one channel. */
data class Channel(
    val key: String,
    val name: String,
    val logo: String?,
    val categories: List<String>,
    val streams: List<StreamSource>,
    val number: Int = 0,
)

data class Country(val code: String, val name: String, val flag: String) {
    val label get() = if (flag.isBlank()) name else "$flag  $name"
}

object Categories {
    // Order the channel numbers follow, like a normal TV line up.
    private val order = listOf(
        "general", "news", "entertainment", "movies", "series", "sports", "kids", "animation",
        "family", "music", "documentary", "comedy", "lifestyle", "cooking", "travel", "outdoor",
        "culture", "education", "science", "business", "religious", "legislative", "classic",
        "auto", "weather", "shop", "public", "relax", "interactive", "undefined",
    )

    fun rank(category: String): Int = order.indexOf(category).let { if (it < 0) order.size - 1 else it }

    fun label(category: String): String =
        if (category == "undefined") "Other" else category.replaceFirstChar { it.uppercase() }
}
