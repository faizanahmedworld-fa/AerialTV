package tv.aerialtv.app

import kotlin.math.abs

/**
 * Reads iptv-org M3U playlists. Entries that share a tvg-id (SD, HD and backup feeds of the
 * same channel) become one channel with several streams, so a dead link falls back quietly.
 */
object M3uParser {

    private val attrRegex = Regex("""([A-Za-z0-9-]+)="([^"]*)"""")
    private val qualityRegex = Regex("""\((\d{3,4})p\)""")
    private val tagRegex = Regex("""\[[^\]]*]""")
    private val spaceRegex = Regex("""\s+""")

    private class Builder(val key: String, var name: String, var logo: String?) {
        val categories = LinkedHashSet<String>()
        val streams = ArrayList<StreamSource>()
    }

    fun parse(text: String): List<Channel> {
        val byKey = LinkedHashMap<String, Builder>()
        var info: String? = null
        var userAgent: String? = null
        var referrer: String? = null

        for (raw in text.trimStart('﻿').lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF") -> {
                    info = line
                    userAgent = null
                    referrer = null
                }
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> userAgent = line.substringAfter('=')
                line.startsWith("#EXTVLCOPT:http-referrer=") -> referrer = line.substringAfter('=')
                line.startsWith("#") -> Unit
                else -> {
                    val header = info ?: continue
                    // Only web streams. The player has no rtsp, rtmp or udp support and would
                    // crash trying, and the iptv-org lists hold a handful of those.
                    if (isHttp(line)) add(byKey, header, line, userAgent, referrer)
                    info = null
                }
            }
        }

        return byKey.values
            .filter { it.streams.isNotEmpty() }
            .map { b ->
                Channel(
                    key = b.key,
                    name = b.name,
                    logo = b.logo,
                    categories = b.categories.sortedBy(Categories::rank),
                    streams = b.streams.sortedWith(compareBy<StreamSource>({ it.geoBlocked }, { qualityScore(it.quality) })),
                )
            }
            .sortedWith(compareBy<Channel>({ Categories.rank(it.categories.first()) }, { it.name.lowercase() }))
            .mapIndexed { i, c -> c.copy(number = i + 1) }
    }

    private fun add(byKey: MutableMap<String, Builder>, header: String, url: String, ua: String?, ref: String?) {
        val (attrPart, namePart) = splitInfo(header)
        val attrs = attrRegex.findAll(attrPart).associate { it.groupValues[1] to it.groupValues[2] }

        val rawName = namePart.trim()
        val quality = qualityRegex.find(rawName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val geoBlocked = rawName.contains("Geo-blocked", ignoreCase = true)
        val name = rawName.replace(qualityRegex, "").replace(tagRegex, "").replace(spaceRegex, " ").trim()
            .ifEmpty { rawName }

        val key = attrs["tvg-id"]?.substringBefore('@')?.takeIf { it.isNotBlank() }?.lowercase()
            ?: name.lowercase()

        val b = byKey.getOrPut(key) { Builder(key, name, null) }
        if (name.length < b.name.length) b.name = name
        if (b.logo.isNullOrBlank()) b.logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }

        val groups = attrs["group-title"].orEmpty().split(';')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        b.categories += groups.ifEmpty { listOf("undefined") }

        if (b.streams.none { it.url == url }) {
            b.streams += StreamSource(
                url = url,
                userAgent = ua ?: attrs["http-user-agent"],
                referrer = ref ?: attrs["http-referrer"],
                quality = quality,
                geoBlocked = geoBlocked,
            )
        }
    }

    private fun isHttp(url: String) = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    /** Splits "#EXTINF:-1 a="x, y",Name" at the first comma that is not inside quotes. */
    private fun splitInfo(line: String): Pair<String, String> {
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return line.substring(0, i) to line.substring(i + 1)
        }
        return line to ""
    }

    // 720p first: sharp on a TV and still smooth on an average home connection.
    private fun qualityScore(q: Int) = if (q == 0) 150 else abs(q - 720)
}
