package tv.aerialtv.app

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** How often and how long one channel has been watched on this device. */
data class WatchStat(val count: Int, val seconds: Long, val last: Long)

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("aerial", Context.MODE_PRIVATE)

    var country: String?
        get() = sp.getString("country", null)
        set(value) = sp.edit().putString("country", value).apply()

    /** True once the first run steps have been walked through (or skipped) at least once. */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) = sp.edit().putBoolean("onboarded", value).apply()

    var profile: Profile
        get() = Profile.from(sp.getString("profile", null))
        set(value) = sp.edit().putString("profile", value.id).apply()

    var interests: Set<String>
        get() = sp.getStringSet("interests", null)?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("interests", value).apply()

    /** ISO 639-3 codes, the ones iptv-org names its language playlists with. */
    var languages: Set<String>
        get() = sp.getStringSet("languages", null)?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("languages", value).apply()

    fun lastChannel(country: String): String? = sp.getString("last_$country", null)

    fun setLastChannel(country: String, key: String) = sp.edit().putString("last_$country", key).apply()

    fun favorites(): Set<String> = sp.getStringSet("favorites", null)?.toSet() ?: emptySet()

    /** Returns true when the channel was added, false when it was removed. */
    fun toggleFavorite(key: String): Boolean {
        val set = favorites().toMutableSet()
        val added = set.add(key)
        if (!added) set.remove(key)
        sp.edit().putStringSet("favorites", set).apply()
        return added
    }

    // region watch history

    fun history(country: String): Map<String, WatchStat> {
        val raw = sp.getString("history_$country", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap {
                for (key in o.keys()) {
                    val s = o.getJSONObject(key)
                    put(key, WatchStat(s.optInt("n"), s.optLong("s"), s.optLong("t")))
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Adds one sitting on a channel. The count goes up when it starts playing, the seconds when
     * the viewer leaves, so zapping past a channel for two seconds barely moves it.
     */
    fun recordWatch(country: String, key: String, seconds: Long, newVisit: Boolean) {
        val map = history(country).toMutableMap()
        val old = map[key] ?: WatchStat(0, 0, 0)
        map[key] = WatchStat(
            count = old.count + if (newVisit) 1 else 0,
            seconds = old.seconds + seconds,
            last = System.currentTimeMillis(),
        )
        // Keep the file small: only the 60 channels that matter most survive.
        val kept = map.entries.sortedByDescending { it.value.seconds + it.value.count * 60 }.take(60)
        val o = JSONObject()
        kept.forEach { (k, v) ->
            o.put(k, JSONObject().put("n", v.count).put("s", v.seconds).put("t", v.last))
        }
        sp.edit().putString("history_$country", o.toString()).apply()
    }

    fun clearHistoryAndFavorites() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("history_") || it.startsWith("last_") }.forEach { e.remove(it) }
        e.remove("favorites").apply()
    }

    // endregion
}

object Countries {

    fun all(context: Context): List<Country> {
        val json = context.assets.open("countries.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Country(o.getString("code"), o.getString("name"), o.optString("flag"))
        }
    }

    /**
     * Likely countries, best first: mobile network, SIM, time zone, then device language.
     * TV boxes have no SIM and often ship set to US English, but their time zone is usually right.
     */
    fun detect(context: Context, all: List<Country>): List<Country> {
        val tm = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (e: Exception) {
            null
        }
        val guesses = listOf(tm?.networkCountryIso, tm?.simCountryIso, timeZoneRegion(), Locale.getDefault().country)
        return guesses
            .mapNotNull { guess ->
                val code = guess?.lowercase()?.takeIf { it.isNotBlank() }?.let { if (it == "gb") "uk" else it }
                all.firstOrNull { it.code == code }
            }
            .distinct()
    }

    private fun timeZoneRegion(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.icu.util.TimeZone.getRegion(java.util.TimeZone.getDefault().id)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
}

object Languages {

    fun all(context: Context): List<Language> {
        val json = context.assets.open("languages.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val countries = o.optJSONArray("countries")?.let { c -> List(c.length()) { c.getString(it) } }.orEmpty()
            Language(o.getString("code"), o.getString("name"), countries)
        }
    }

    /** Languages people in this country usually want, then the device language. */
    fun suggested(all: List<Language>, country: String?): List<Language> {
        val fromCountry = if (country == null) emptyList() else all.filter { country in it.countries }
        val device = try { Locale.getDefault().isO3Language.lowercase() } catch (e: Exception) { "" }
        val fromDevice = all.filter { it.code == device }
        return (fromCountry + fromDevice).distinct()
    }
}
