package tv.aerialtv.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The first run walk through: country, who's watching, interests, languages, done. Settings
 * opens the same screen on one step at a time to change an answer later.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var stepLabel: TextView
    private lateinit var dots: LinearLayout
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var sideIcon: ImageView
    private lateinit var content: ViewGroup
    private lateinit var skipButton: Button
    private lateinit var nextButton: Button

    private lateinit var countrySearch: EditText
    private lateinit var countryList: RecyclerView
    private lateinit var profileList: RecyclerView
    private lateinit var interestList: RecyclerView
    private lateinit var languageSearch: EditText
    private lateinit var languageList: RecyclerView
    private lateinit var languageCount: TextView
    private lateinit var summaryCountry: TextView
    private lateinit var summaryProfile: TextView
    private lateinit var summaryInterests: TextView
    private lateinit var summaryLanguages: TextView

    private lateinit var countryAdapter: PickAdapter
    private lateinit var profileAdapter: ChoiceAdapter
    private lateinit var interestAdapter: ChipAdapter
    private lateinit var languageAdapter: PickAdapter

    private var allCountries: List<Country> = emptyList()
    private var allLanguages: List<Language> = emptyList()

    // Answers are staged here and written to Prefs when a step is left, so Back on a settings
    // edit throws the change away.
    private var country: String? = null
    private var profile = Profile.FAMILY
    private var interests: Set<String> = emptySet()
    private var languages: Set<String> = emptySet()

    private var steps: List<Int> = emptyList()
    private var index = 0
    private var editing = false
    private var forward = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        prefs = Prefs(this)
        allCountries = Countries.all(this)
        allLanguages = Languages.all(this)
        country = prefs.country
        profile = prefs.profile
        interests = prefs.interests
        languages = prefs.languages

        editing = intent.getBooleanExtra(EXTRA_EDIT, false)
        val only = intent.getIntExtra(EXTRA_STEP, -1)
        steps = if (editing && only >= 0) listOf(only) else listOf(STEP_COUNTRY, STEP_PROFILE, STEP_INTERESTS, STEP_LANGUAGES, STEP_DONE)

        bindViews()
        setupCountry()
        setupProfile()
        setupInterests()
        setupLanguages()
        setupButtons()
        showStep(0, animate = false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    editing -> cancel()
                    index > 0 -> { forward = false; showStep(index - 1, animate = true) }
                    else -> cancel()
                }
            }
        })
    }

    private fun cancel() {
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_USER_CANCELLED, true))
        finish()
    }

    private fun bindViews() {
        stepLabel = findViewById(R.id.stepLabel)
        dots = findViewById(R.id.stepDots)
        title = findViewById(R.id.stepTitle)
        subtitle = findViewById(R.id.stepSubtitle)
        sideIcon = findViewById(R.id.stepIcon)
        content = findViewById(R.id.stepContent)
        skipButton = findViewById(R.id.skipButton)
        nextButton = findViewById(R.id.nextButton)
        countrySearch = findViewById(R.id.countrySearch)
        countryList = findViewById(R.id.countryList)
        profileList = findViewById(R.id.stepProfile)
        interestList = findViewById(R.id.stepInterests)
        languageSearch = findViewById(R.id.languageSearch)
        languageList = findViewById(R.id.languageList)
        languageCount = findViewById(R.id.languageCount)
        summaryCountry = findViewById(R.id.summaryCountry)
        summaryProfile = findViewById(R.id.summaryProfile)
        summaryInterests = findViewById(R.id.summaryInterests)
        summaryLanguages = findViewById(R.id.summaryLanguages)

        dots.removeAllViews()
        if (steps.size > 1) {
            steps.forEach { _ ->
                val dot = View(this)
                dot.setBackgroundResource(R.drawable.bg_dot)
                val size = (10 * resources.displayMetrics.density).toInt()
                dots.addView(dot, LinearLayout.LayoutParams(size, size).also { it.marginEnd = size })
            }
        }
    }

    // region steps

    private fun setupCountry() {
        countryAdapter = PickAdapter(PickAdapter.Trailing.TICK) { row ->
            country = row.id
            countrySearch.setText("")
            countryAdapter.submit(countryRows(""))
            if (editing) finishEdit() else handler.postDelayed({ next() }, 220)
        }
        countryList.layoutManager = LinearLayoutManager(this)
        countryList.adapter = countryAdapter
        countryList.itemAnimator = null
        countrySearch.doAfterTextChanged { countryAdapter.submit(countryRows(it?.toString().orEmpty())) }
        countrySearch.setOnEditorActionListener { _, _, _ -> focusFirst(countryList, countryAdapter.firstItemPosition()); true }
    }

    private fun countryRows(query: String): List<PickRow> {
        val q = query.trim().lowercase()
        val chosen = allCountries.firstOrNull { it.code == country }
        if (q.isNotEmpty()) {
            val hits = allCountries.filter { it.name.lowercase().contains(q) || it.code == q }
            if (hits.isEmpty()) return listOf(PickRow.Header(getString(R.string.nothing_matches)))
            return hits.map { it.toRow(it.code == country) }
        }
        val suggested = (listOfNotNull(chosen) + Countries.detect(this, allCountries).take(2) +
            listOfNotNull(allCountries.firstOrNull { it.code == "int" })).distinct()
        return buildList {
            if (suggested.isNotEmpty()) {
                add(PickRow.Header(getString(R.string.suggested)))
                suggested.forEach { add(it.toRow(it.code == country)) }
            }
            add(PickRow.Header(getString(R.string.all_countries)))
            allCountries.filter { it !in suggested }.forEach { add(it.toRow(it.code == country)) }
        }
    }

    private fun Country.toRow(selected: Boolean) = PickRow.Item(code, name, emoji = flag, selected = selected)

    private fun setupProfile() {
        profileAdapter = ChoiceAdapter { c ->
            profile = Profile.from(c.id)
            profileAdapter.selected = c.id
            if (editing) finishEdit() else handler.postDelayed({ next() }, 260)
        }
        profileList.layoutManager = LinearLayoutManager(this)
        profileList.adapter = profileAdapter
        profileList.itemAnimator = null
        profileAdapter.submit(
            listOf(
                Choice(Profile.KIDS.id, R.drawable.ic_kids, getString(R.string.profile_kids), getString(R.string.profile_kids_desc)),
                Choice(Profile.FAMILY.id, R.drawable.ic_family, getString(R.string.profile_family), getString(R.string.profile_family_desc)),
                Choice(Profile.ADULTS.id, R.drawable.ic_person, getString(R.string.profile_adults), getString(R.string.profile_adults_desc)),
            ),
        )
    }

    private fun setupInterests() {
        interestAdapter = ChipAdapter { _, _ -> interests = interestAdapter.chosen() }
        interestList.layoutManager = GridLayoutManager(this, 2)
        interestList.adapter = interestAdapter
        interestList.itemAnimator = null
    }

    private fun setupLanguages() {
        languageAdapter = PickAdapter(PickAdapter.Trailing.TICK) { row ->
            languages = if (row.id in languages) languages - row.id else languages + row.id
            languageAdapter.setSelected(row.id, row.id in languages)
            languageCount.text = getString(R.string.chosen_count, languages.size)
        }
        languageList.layoutManager = LinearLayoutManager(this)
        languageList.adapter = languageAdapter
        languageList.itemAnimator = null
        languageSearch.doAfterTextChanged { languageAdapter.submit(languageRows(it?.toString().orEmpty())) }
        languageSearch.setOnEditorActionListener { _, _, _ -> focusFirst(languageList, languageAdapter.firstItemPosition()); true }
    }

    private fun languageRows(query: String): List<PickRow> {
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            val hits = allLanguages.filter { it.name.lowercase().contains(q) }
            if (hits.isEmpty()) return listOf(PickRow.Header(getString(R.string.nothing_matches)))
            return hits.map { it.toRow() }
        }
        val suggested = (allLanguages.filter { it.code in languages } + Languages.suggested(allLanguages, country)).distinct()
        return buildList {
            if (suggested.isNotEmpty()) {
                add(PickRow.Header(getString(R.string.suggested)))
                suggested.forEach { add(it.toRow()) }
            }
            add(PickRow.Header(getString(R.string.all_languages)))
            allLanguages.filter { it !in suggested }.sortedBy { it.name }.forEach { add(it.toRow()) }
        }
    }

    private fun Language.toRow() = PickRow.Item(code, name, icon = R.drawable.ic_language, selected = code in languages)

    private fun setupButtons() {
        skipButton.setOnClickListener { next() }
        nextButton.setOnClickListener { if (editing) finishEdit() else next() }
    }

    private fun showStep(i: Int, animate: Boolean) {
        index = i
        val step = steps[i]

        // Left pane text, with a quick slide in the direction of travel.
        if (animate) {
            val anim = AnimationUtils.loadAnimation(this, if (forward) R.anim.slide_in_right else R.anim.slide_in_left)
            findViewById<View>(R.id.stepSide).startAnimation(anim)
        }
        stepLabel.text = if (steps.size > 1) getString(R.string.step_of, i + 1, steps.size) else getString(R.string.settings)
        for (d in 0 until dots.childCount) dots.getChildAt(d).isActivated = d <= i
        val (titleRes, subtitleRes, icon) = when (step) {
            STEP_COUNTRY -> Triple(R.string.step_country_title, R.string.step_country_subtitle, R.drawable.ic_globe)
            STEP_PROFILE -> Triple(R.string.step_profile_title, R.string.step_profile_subtitle, R.drawable.ic_family)
            STEP_INTERESTS -> Triple(R.string.step_interests_title, R.string.step_interests_subtitle, R.drawable.ic_sparkle)
            STEP_LANGUAGES -> Triple(R.string.step_languages_title, R.string.step_languages_subtitle, R.drawable.ic_language)
            else -> Triple(R.string.step_done_title, R.string.step_done_subtitle, R.drawable.ic_check)
        }
        title.setText(titleRes)
        subtitle.setText(subtitleRes)
        sideIcon.setImageResource(icon)

        // Right pane: hide every step view but this one.
        val views = mapOf(
            STEP_COUNTRY to R.id.stepCountry, STEP_PROFILE to R.id.stepProfile, STEP_INTERESTS to R.id.stepInterests,
            STEP_LANGUAGES to R.id.stepLanguages, STEP_DONE to R.id.stepDone,
        )
        views.forEach { (s, id) ->
            val v = findViewById<View>(id)
            if (s == step) {
                v.visibility = View.VISIBLE
                if (animate) v.startAnimation(AnimationUtils.loadAnimation(this, if (forward) R.anim.slide_in_right else R.anim.slide_in_left))
            } else {
                v.visibility = View.GONE
            }
        }

        // Buttons: single pick steps move on by themselves, so they only need Skip.
        val last = i == steps.lastIndex
        skipButton.visibility = if (editing || step == STEP_DONE) View.GONE else View.VISIBLE
        nextButton.visibility = if (!editing && step in listOf(STEP_COUNTRY, STEP_PROFILE)) View.GONE else View.VISIBLE
        nextButton.text = getString(
            when {
                editing -> R.string.save
                last -> R.string.start_watching
                else -> R.string.next
            },
        )
        // Country has to be answered on a first run. Skip is for the questions that are optional.
        if (step == STEP_COUNTRY && !editing) skipButton.visibility = View.GONE

        when (step) {
            STEP_COUNTRY -> {
                countryAdapter.submit(countryRows(countrySearch.text.toString()))
                countryList.riseIn()
                focusFirst(countryList, countryAdapter.firstItemPosition())
            }
            STEP_PROFILE -> {
                profileAdapter.selected = profile.id
                profileList.riseIn()
                focusFirst(profileList, Profile.entries.indexOf(profile).coerceAtLeast(0))
            }
            STEP_INTERESTS -> {
                interestAdapter.submit(Interests.all.map { Chip(it.id, it.icon, getString(it.labelRes)) }, interests)
                interestList.riseIn()
                focusFirst(interestList, 0)
            }
            STEP_LANGUAGES -> {
                languageAdapter.submit(languageRows(languageSearch.text.toString()))
                languageCount.text = getString(R.string.chosen_count, languages.size)
                languageList.riseIn()
                focusFirst(languageList, languageAdapter.firstItemPosition())
            }
            STEP_DONE -> {
                summaryCountry.text = allCountries.firstOrNull { it.code == country }?.label ?: getString(R.string.not_set)
                summaryProfile.text = getString(profileLabel(profile))
                summaryInterests.text = interests.mapNotNull(Interests::byId).joinToString(", ") { getString(it.labelRes) }
                    .ifEmpty { getString(R.string.not_set) }
                summaryLanguages.text = allLanguages.filter { it.code in languages }.joinToString(", ") { it.name }
                    .ifEmpty { getString(R.string.not_set) }
                findViewById<View>(R.id.stepDone).let { it.alpha = 0f; it.fadeIn(320, 16f) }
                nextButton.requestFocus()
            }
        }
    }

    private fun next() {
        commit()
        if (index == steps.lastIndex) {
            if (country == null) return
            prefs.onboarded = true
            setResult(RESULT_OK)
            finish()
            return
        }
        forward = true
        showStep(index + 1, animate = true)
    }

    private fun finishEdit() {
        commit()
        setResult(RESULT_OK)
        finish()
    }

    private fun commit() {
        country?.let { prefs.country = it }
        prefs.profile = profile
        prefs.interests = interests
        prefs.languages = languages
    }

    // endregion

    private fun focusFirst(list: RecyclerView, position: Int) {
        if (position < 0) return
        list.post {
            (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset((position - 1).coerceAtLeast(0), 0)
            list.post { list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() ?: list.requestFocus() }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STEP = "step"
        const val EXTRA_EDIT = "edit"
        const val EXTRA_USER_CANCELLED = "user_cancelled"
        const val STEP_COUNTRY = 0
        const val STEP_PROFILE = 1
        const val STEP_INTERESTS = 2
        const val STEP_LANGUAGES = 3
        const val STEP_DONE = 4

        fun profileLabel(p: Profile) = when (p) {
            Profile.KIDS -> R.string.profile_kids
            Profile.FAMILY -> R.string.profile_family
            Profile.ADULTS -> R.string.profile_adults
        }

        fun edit(from: android.content.Context, step: Int) =
            Intent(from, OnboardingActivity::class.java).putExtra(EXTRA_EDIT, true).putExtra(EXTRA_STEP, step)
    }
}
