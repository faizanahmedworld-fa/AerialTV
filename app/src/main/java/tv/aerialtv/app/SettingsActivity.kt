package tv.aerialtv.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Everything the viewer answered at first run, editable one row at a time. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: PickAdapter
    private var changed = false

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
            when (row.id) {
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
        val list = findViewById<RecyclerView>(R.id.settingsList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null
        adapter.submit(rows())
        list.riseIn()
        list.post { list.post { list.findViewHolderForAdapterPosition(1)?.itemView?.requestFocus() ?: list.requestFocus() } }
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
            PickRow.Item(ROW_RERUN, getString(R.string.settings_rerun), getString(R.string.settings_rerun_desc), icon = R.drawable.ic_replay),
            PickRow.Item(ROW_CLEAR, getString(R.string.settings_clear), getString(R.string.settings_clear_desc), icon = R.drawable.ic_delete),
            PickRow.Item(ROW_ABOUT, getString(R.string.settings_about), getString(R.string.settings_about_desc, BuildConfig.VERSION_NAME), icon = R.drawable.ic_info),
        )
    }

    private companion object {
        const val ROW_COUNTRY = "country"
        const val ROW_PROFILE = "profile"
        const val ROW_INTERESTS = "interests"
        const val ROW_LANGUAGES = "languages"
        const val ROW_RERUN = "rerun"
        const val ROW_CLEAR = "clear"
        const val ROW_ABOUT = "about"
    }
}
