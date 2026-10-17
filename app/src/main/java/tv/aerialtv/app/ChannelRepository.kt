package tv.aerialtv.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel as ResultChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a country's playlist. iptv-org.github.io is blocked by some ISPs, so the same files
 * are tried from GitHub raw and jsDelivr too. The last good copy is kept for offline starts.
 */
class ChannelRepository(private val context: Context) {

    private val sources = buildList {
        if (BuildConfig.MIRROR_URL.isNotBlank()) add(BuildConfig.MIRROR_URL)
        add("https://iptv-org.github.io/iptv/countries/%s.m3u")
        add("https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/countries/%s.m3u")
        add("https://cdn.jsdelivr.net/gh/iptv-org/iptv@gh-pages/countries/%s.m3u")
    }

    suspend fun load(country: String): List<Channel> = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, "playlist_$country.m3u")
        val age = System.currentTimeMillis() - cache.lastModified()

        if (cache.exists() && age < CACHE_MS) {
            val cached = M3uParser.parse(cache.readText())
            if (cached.isNotEmpty()) return@withContext cached
        }

        val text = downloadFirst(country)
        if (text != null) {
            cache.writeText(text)
            return@withContext M3uParser.parse(text)
        }

        if (cache.exists()) return@withContext M3uParser.parse(cache.readText())
        throw IOException("No playlist source reachable")
    }

    /**
     * Starts each source a moment after the previous one and takes whichever answers first.
     * A blocked host can hang for 20 seconds before failing, and nobody should wait for that.
     */
    private suspend fun downloadFirst(country: String): String? {
        val results = ResultChannel<String?>(sources.size)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        sources.forEachIndexed { i, pattern ->
            scope.launch {
                delay(i * STAGGER_MS)
                results.send(fetch(pattern.format(country))?.takeIf { "#EXTINF" in it })
            }
        }
        try {
            repeat(sources.size) { results.receive()?.let { return it } }
            return null
        } finally {
            scope.cancel()
        }
    }

    private fun fetch(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "AerialTV/${BuildConfig.VERSION_NAME} (Android)")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else conn.inputStream.bufferedReader().use { it.readText() }
                .takeIf { it.trimStart('﻿').startsWith("#EXTM3U") }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val CACHE_MS = 6 * 60 * 60 * 1000L
        const val STAGGER_MS = 2_500L
    }
}
