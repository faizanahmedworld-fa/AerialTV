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

/** A language iptv-org publishes a playlist for, plus the countries where people tend to want it. */
data class Language(val code: String, val name: String, val countries: List<String>)

/** Who is holding the remote. Changes what "For you" puts first. */
enum class Profile(val id: String) {
    KIDS("kids"), FAMILY("family"), ADULTS("adults");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: FAMILY

        /** Categories that are fine when a child is watching on their own. */
        val kidSafe = setOf(
            "kids", "animation", "family", "education", "science", "music", "culture",
            "cooking", "travel", "outdoor", "relax", "weather",
        )
    }
}

/** An interest the viewer can tick during setup. Each one covers a few iptv-org categories. */
data class Interest(val id: String, val categories: Set<String>, val icon: Int, val labelRes: Int)

object Interests {
    val all = listOf(
        Interest("news", setOf("news", "legislative", "public"), R.drawable.ic_news, R.string.interest_news),
        Interest("sports", setOf("sports", "outdoor"), R.drawable.ic_sports, R.string.interest_sports),
        Interest("movies", setOf("movies", "series", "classic"), R.drawable.ic_movie, R.string.interest_movies),
        Interest("kids", setOf("kids", "animation", "family"), R.drawable.ic_kids, R.string.interest_kids),
        Interest("music", setOf("music"), R.drawable.ic_music, R.string.interest_music),
        Interest("entertainment", setOf("entertainment", "comedy", "lifestyle", "general"), R.drawable.ic_entertainment, R.string.interest_entertainment),
        Interest("learning", setOf("documentary", "education", "science", "culture"), R.drawable.ic_learning, R.string.interest_learning),
        Interest("religious", setOf("religious"), R.drawable.ic_religious, R.string.interest_religious),
        Interest("business", setOf("business"), R.drawable.ic_business, R.string.interest_business),
        Interest("food", setOf("cooking", "travel"), R.drawable.ic_food, R.string.interest_food),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

object Categories {
    // Order the channel numbers follow, like a normal TV line up.
    private val order = listOf(
        "general", "news", "entertainment", "movies", "series", "sports", "kids", "animation",
        "family", "music", "documentary", "comedy", "lifestyle", "cooking", "travel", "outdoor",
        "culture", "education", "science", "business", "religious", "legislative", "classic",
        "auto", "weather", "shop", "public", "relax", "interactive", "undefined",
    )

    private val icons = mapOf(
        "general" to R.drawable.ic_tv,
        "news" to R.drawable.ic_news,
        "entertainment" to R.drawable.ic_entertainment,
        "movies" to R.drawable.ic_movie,
        "series" to R.drawable.ic_movie,
        "sports" to R.drawable.ic_sports,
        "kids" to R.drawable.ic_kids,
        "animation" to R.drawable.ic_kids,
        "family" to R.drawable.ic_family,
        "music" to R.drawable.ic_music,
        "documentary" to R.drawable.ic_learning,
        "comedy" to R.drawable.ic_entertainment,
        "lifestyle" to R.drawable.ic_entertainment,
        "cooking" to R.drawable.ic_food,
        "travel" to R.drawable.ic_globe,
        "outdoor" to R.drawable.ic_sports,
        "culture" to R.drawable.ic_learning,
        "education" to R.drawable.ic_learning,
        "science" to R.drawable.ic_learning,
        "business" to R.drawable.ic_business,
        "religious" to R.drawable.ic_religious,
        "legislative" to R.drawable.ic_news,
        "classic" to R.drawable.ic_movie,
        "auto" to R.drawable.ic_car,
        "weather" to R.drawable.ic_weather,
        "shop" to R.drawable.ic_business,
        "public" to R.drawable.ic_news,
        "relax" to R.drawable.ic_weather,
    )

    fun rank(category: String): Int = order.indexOf(category).let { if (it < 0) order.size - 1 else it }

    private val labels = mapOf(
        "general" to R.string.category_general,
        "news" to R.string.category_news,
        "entertainment" to R.string.category_entertainment,
        "movies" to R.string.category_movies,
        "series" to R.string.category_series,
        "sports" to R.string.category_sports,
        "kids" to R.string.category_kids,
        "animation" to R.string.category_animation,
        "family" to R.string.category_family,
        "music" to R.string.category_music,
        "documentary" to R.string.category_documentary,
        "comedy" to R.string.category_comedy,
        "lifestyle" to R.string.category_lifestyle,
        "cooking" to R.string.category_cooking,
        "travel" to R.string.category_travel,
        "outdoor" to R.string.category_outdoor,
        "culture" to R.string.category_culture,
        "education" to R.string.category_education,
        "science" to R.string.category_science,
        "business" to R.string.category_business,
        "religious" to R.string.category_religious,
        "legislative" to R.string.category_legislative,
        "classic" to R.string.category_classic,
        "auto" to R.string.category_auto,
        "weather" to R.string.category_weather,
        "shop" to R.string.category_shop,
        "public" to R.string.category_public,
        "relax" to R.string.category_relax,
        "interactive" to R.string.category_interactive,
        "undefined" to R.string.category_undefined,
    )

    /** The category in the menu language. Anything iptv-org adds later shows in plain English. */
    fun label(context: android.content.Context, category: String): String =
        labels[category]?.let { context.getString(it) } ?: category.replaceFirstChar { it.uppercase() }

    fun icon(category: String): Int = icons[category] ?: R.drawable.ic_label
}
