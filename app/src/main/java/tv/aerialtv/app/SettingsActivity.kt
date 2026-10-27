package tv.aerialtv.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Everything the viewer answered at first run, editable one row at a time. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: PickAdapter
    private lateinit var list: RecyclerView
    private var changed = false
    private var pickingLanguage = false

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            changed = true
            setResult(RESULT_OK)
        }
        adapter.submit(rows())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        adapter = PickAdapter(PickAdapter.Trailing.ARROW) { row ->
            if (pickingLanguage) {
                pickLanguage(row.id.removePrefix(LANG))
                return@PickAdapter
            }
            when (row.id) {
                ROW_APP_LANGUAGE -> showLanguages()
                ROW_COUNTRY -> editor.launch(OnboardingActivity.edit(this, OnboardingActivity.STEP_COUNTRY))
                ROW_PROFILE -> editor.launch(OnboardingActivity.edit(this, OnboardingActivity.STEP_PROFILE))
                ROW_INTERESTS -> editor.launch(OnboardingActivity.edit(this, OnboardingActivity.STEP_INTERESTS))
                ROW_LANGUAGES -> editor.launch(OnboardingActivity.edit(this, OnboardingActivity.STEP_LANGUAGES))
                ROW_RERUN -> editor.launch(Intent(this, OnboardingActivity::class.java))
                ROW_CLEAR -> {
                    prefs.clearHistoryAndFavorites()
                    changed = true
                    setResult(RESULT_OK)
                    Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
                }
            }
        }
        list = findViewById(R.id.settingsList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null
        adapter.submit(rows())
        list.riseIn()
        focusRow(1)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pickingLanguage) showMain() else finish()
            }
        })
    }

    private fun focusRow(position: Int) {
        list.post { list.post { list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() ?: list.requestFocus() } }
    }

    private fun showLanguages() {
        pickingLanguage = true
        adapter.trailing = PickAdapter.Trailing.TICK
        val current = AppLanguages.current()
        val rows = buildList {
            add(PickRow.Header(getString(R.string.app_language_title)))
            add(PickRow.Item(LANG, getString(R.string.app_language_system), icon = R.drawable.ic_tv, selected = current.isBlank()))
            AppLanguages.all.forEach {
                add(PickRow.Item(LANG + it.tag, it.name, icon = R.drawable.ic_language, selected = it.tag.equals(current, ignoreCase = true)))
            }
        }
        adapter.submit(rows)
        list.riseIn()
        focusRow(rows.indexOfFirst { it is PickRow.Item && it.selected }.coerceAtLeast(1))
    }

    private fun showMain() {
        pickingLanguage = false
        adapter.trailing = PickAdapter.Trailing.ARROW
        adapter.submit(rows())
        list.riseIn()
        focusRow(6)
    }

    /** Every open screen switches language on the spot; the choice survives restarts. */
    private fun pickLanguage(tag: String) {
        changed = true
        setResult(RESULT_OK)
        pickingLanguage = false
        if (tag.equals(AppLanguages.current(), ignoreCase = true)) {
            showMain()
            return
        }
        AppLanguages.apply(tag)
    }

    private fun rows(): List<PickRow> {
        val countries = Countries.all(this)
        val languages = Languages.all(this)
        val country = countries.firstOrNull { it.code == prefs.country }
        val interests = prefs.interests.mapNotNull(Interests::byId).joinToString(", ") { getString(it.labelRes) }
        val langs = languages.filter { it.code in prefs.languages }.joinToString(", ") { it.name }
        return listOf(
            PickRow.Header(getString(R.string.settings_section_you)),
            PickRow.Item(ROW_COUNTRY, getString(R.string.settings_country), country?.label ?: getString(R.string.not_set), icon = R.drawable.ic_globe),
            PickRow.Item(ROW_PROFILE, getString(R.string.settings_profile), getString(OnboardingActivity.profileLabel(prefs.profile)), icon = R.drawable.ic_family),
            PickRow.Item(ROW_INTERESTS, getString(R.string.settings_interests), interests.ifEmpty { getString(R.string.not_set) }, icon = R.drawable.ic_sparkle),
            PickRow.Item(ROW_LANGUAGES, getString(R.string.settings_languages), langs.ifEmpty { getString(R.string.not_set) }, icon = R.drawable.ic_language),
            PickRow.Header(getString(R.string.settings_section_app)),
            PickRow.Item(ROW_APP_LANGUAGE, getString(R.string.settings_app_language), appLanguageLine(), icon = R.drawable.ic_language),
            PickRow.Item(ROW_RERUN, getString(R.string.settings_rerun), getString(R.string.settings_rerun_desc), icon = R.drawable.ic_replay),
            PickRow.Item(ROW_CLEAR, getString(R.string.settings_clear), getString(R.string.settings_clear_desc), icon = R.drawable.ic_delete),
            PickRow.Item(ROW_ABOUT, getString(R.string.settings_about), getString(R.string.settings_about_desc, BuildConfig.VERSION_NAME), icon = R.drawable.ic_info),
        )
    }

    /** "Same as the TV" on its own, or "Menus and messages are in Deutsch" once one is picked. */
    private fun appLanguageLine(): String =
        if (AppLanguages.current().isBlank()) getString(R.string.app_language_system)
        else getString(R.string.settings_app_language_desc, AppLanguages.currentName(this))

    private companion object {
        const val ROW_COUNTRY = "country"
        const val ROW_PROFILE = "profile"
        const val ROW_INTERESTS = "interests"
        const val ROW_LANGUAGES = "languages"
        const val ROW_APP_LANGUAGE = "app_language"
        const val LANG = "lang:"
        const val ROW_RERUN = "rerun"
        const val ROW_CLEAR = "clear"
        const val ROW_ABOUT = "about"
    }
}
