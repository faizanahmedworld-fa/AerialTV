package tv.aerialtv.app

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Signal strength for a free stream. There is no aerial to measure, so the bars say how
 * quickly the stream's server answers from this device, and later how it actually played.
 *
 * 4 strong, 3 good, 2 fair, 1 weak (not answering), 0 not checked yet.
 */
object StreamProbe {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** Asks up to three of the channel's links and keeps the best answer. */
    suspend fun bars(ch: Channel): Int = withContext(Dispatchers.IO) {
        var best = 1
        for (s in ch.streams.take(3)) {
            val ms = timeToAnswer(s) ?: continue
            val b = when {
                ms < 1_200 -> 4
                ms < 3_000 -> 3
                else -> 2
            }
            if (b > best) best = b
            if (best == 4) break
        }
        best
    }

    /** Milliseconds until the server sends a good status and the first byte, or null when it won't. */
    private fun timeToAnswer(s: StreamSource): Long? {
        val start = SystemClock.elapsedRealtime()
        var url = s.url
        repeat(4) {
            val conn = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return null
            }
            try {
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                // Redirects are followed by hand so http to https hops work too.
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", s.userAgent ?: DEFAULT_USER_AGENT)
                conn.setRequestProperty("Range", "bytes=0-1023")
                s.referrer?.let { conn.setRequestProperty("Referer", it) }
                val code = conn.responseCode
                when (code) {
                    in 200..299 -> {
                        conn.inputStream.use { it.read() }
                        return SystemClock.elapsedRealtime() - start
                    }
                    in 300..399 -> {
                        val next = conn.getHeaderField("Location") ?: return null
                        url = URL(URL(url), next).toString()
                    }
                    else -> return null
                }
            } catch (e: Exception) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }
}

/** One checked channel: the bars and when they were measured. */
data class Health(val bars: Int, val at: Long)

/**
 * Remembers the bars for every channel of the loaded country, in memory and in Prefs, so the
 * list fills in straight away on the next start and only stale channels are checked again.
 */
class HealthStore(private val prefs: Prefs) {

    private val map = HashMap<String, Health>()
    private var country: String? = null
    private var dirty = 0

    fun load(country: String) {
        this.country = country
        map.clear()
        map.putAll(prefs.health(country))
        dirty = 0
    }

    fun bars(key: String): Int = map[key]?.bars ?: 0

    /** True when this channel needs a fresh probe. Played channels are refreshed like the rest. */
    fun isStale(key: String): Boolean {
        val h = map[key] ?: return true
        return System.currentTimeMillis() - h.at > MAX_AGE_MS
    }

    fun set(key: String, bars: Int) {
        val old = map[key]
        map[key] = Health(bars, System.currentTimeMillis())
        if (old?.bars != bars) dirty++
        if (dirty >= SAVE_EVERY) save()
    }

    fun save() {
        val c = country ?: return
        if (dirty == 0) return
        prefs.saveHealth(c, map)
        dirty = 0
    }

    private companion object {
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
        const val SAVE_EVERY = 25
    }
}
