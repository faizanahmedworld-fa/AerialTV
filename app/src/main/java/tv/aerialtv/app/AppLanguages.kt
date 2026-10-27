package tv.aerialtv.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages the menus come in. The channel languages picked at setup are a different
 * thing: those decide which channels get a boost, this decides what the buttons say.
 */
object AppLanguages {

    /** An empty tag means "same as the TV", the system language. */
    data class Option(val tag: String, val name: String)

    val all = listOf(
        Option("en", "English"),
        Option("zh-CN", "简体中文"),
        Option("zh-TW", "繁體中文"),
        Option("ar", "العربية"),
        Option("es", "Español"),
        Option("fr", "Français"),
        Option("de", "Deutsch"),
        Option("pt", "Português"),
        Option("ru", "Русский"),
        Option("hi", "हिन्दी"),
        Option("ur", "اردو"),
        Option("tr", "Türkçe"),
        Option("id", "Bahasa Indonesia"),
    )

    fun current(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    fun currentName(context: Context): String {
        val tag = current()
        if (tag.isBlank()) return context.getString(R.string.app_language_system)
        return all.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.name
            ?: all.firstOrNull { tag.substringBefore('-').equals(it.tag.substringBefore('-'), ignoreCase = true) }?.name
            ?: tag
    }

    /** Switches every open screen to the language. Pass an empty tag to follow the TV again. */
    fun apply(tag: String) {
        val locales = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
