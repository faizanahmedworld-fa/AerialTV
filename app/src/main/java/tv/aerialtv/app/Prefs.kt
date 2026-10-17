package tv.aerialtv.app

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import org.json.JSONArray
import java.util.Locale

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("aerial", Context.MODE_PRIVATE)

    var country: String?
        get() = sp.getString("country", null)
        set(value) = sp.edit().putString("country", value).apply()

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
