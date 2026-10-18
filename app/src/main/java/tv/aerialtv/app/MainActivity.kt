package tv.aerialtv.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The whole app is one screen that behaves like a TV: it opens straight onto a channel,
 * up/down changes channel, OK opens the channel list. Dead streams are skipped on their own.
 */
@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: ChannelRepository
    private lateinit var player: ExoPlayer

    private lateinit var playerView: PlayerView
    private lateinit var loading: View
    private lateinit var loadingText: TextView
    private lateinit var message: View
    private lateinit var messageText: TextView
    private lateinit var retryButton: Button
    private lateinit var messageCountryButton: Button
    private lateinit var messageSettingsButton: Button
    private lateinit var banner: View
    private lateinit var bannerNumber: TextView
    private lateinit var bannerLogo: ImageView
    private lateinit var bannerName: TextView
    private lateinit var bannerInfo: TextView
    private lateinit var numberEntry: TextView
    private lateinit var guide: View
    private lateinit var guidePanel: View
    private lateinit var guideCountry: Button
    private lateinit var guideSearch: EditText
    private lateinit var guideSettings: View
    private lateinit var guideHeading: TextView
    private lateinit var guideEmpty: TextView
    private lateinit var categoryList: RecyclerView
    private lateinit var channelList: RecyclerView

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter

    private var countries: List<Country> = emptyList()
    private var languageNames: Map<String, String> = emptyMap()
    private var loadedCountry: String? = null
    private var channels: List<Channel> = emptyList()
    private var channelLanguages: Map<String, Set<String>> = emptyMap()
    private var forYou: List<Channel> = emptyList()
    private var mostWatched: List<Channel> = emptyList()
    private var current = -1
    private var previous = -1
    private var pending = -1
    private var direction = 1
    private var attempts: List<Attempt> = emptyList()
    private var attemptIndex = 0
    private var retries = 0
    private var wasReady = false
    private var skippedInRow = 0
    private var selectedCategory = FOR_YOU
    private var digits = ""
    private var lastBackPress = 0L
    private var pickingCountry = false
    private var guideHiding = false
    private var loadJob: Job? = null
    private var languageJob: Job? = null
    private var toast: Toast? = null
    private var watchedKey: String? = null
    private var watchStart = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val hideBannerTask = Runnable { banner.fadeOut(220, 16f) }
    private val tunePendingTask = Runnable { if (pending >= 0) tune(pending) }
    private val stallTask = Runnable { tryNextAttempt() }
    private val retryTask = Runnable { playAttempt() }
    private val digitsTask = Runnable { jumpToNumber() }
    private var categoryTask: Runnable? = null

    private data class Attempt(val stream: StreamSource, val mimeType: String?)

    private val setup =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pickingCountry = false
            when {
                result.resultCode == RESULT_OK -> applyPrefs()
                prefs.country == null -> {
                    // Back on the first run closes the app. Anything else that ended the setup
                    // without a choice (Home, then the app icon) just opens it again.
                    if (result.data?.getBooleanExtra(OnboardingActivity.EXTRA_USER_CANCELLED, false) == true) finish()
                    else openSetup()
                }
                channels.isNotEmpty() && current >= 0 -> tune(current)
            }
        }

    private val settings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pickingCountry = false
            if (result.resultCode == RESULT_OK) applyPrefs()
            else if (channels.isNotEmpty() && current >= 0) tune(current)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()

        prefs = Prefs(this)
        repo = ChannelRepository(this)
        countries = Countries.all(this)
        languageNames = Languages.all(this).associate { it.code to it.name }

        bindViews()
        setupPlayer()
        setupGuide()
        setupTouch()
        setupBack()

        if (prefs.country == null || !prefs.onboarded) openSetup() else loadChannels()
    }

    override fun onStart() {
        super.onStart()
        if (!pickingCountry && channels.isNotEmpty() && current >= 0 && player.playbackState == Player.STATE_IDLE) {
            tune(current)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(stallTask)
        handler.removeCallbacks(retryTask)
        handler.removeCallbacks(tunePendingTask)
        clearDigits()
        closeWatch()
        player.stop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // region setup

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun bindViews() {
        playerView = findViewById(R.id.player)
        loading = findViewById(R.id.loading)
        loadingText = findViewById(R.id.loadingText)
        message = findViewById(R.id.message)
        messageText = findViewById(R.id.messageText)
        retryButton = findViewById(R.id.retryButton)
        messageCountryButton = findViewById(R.id.messageCountryButton)
        messageSettingsButton = findViewById(R.id.messageSettingsButton)
        banner = findViewById(R.id.banner)
        bannerNumber = findViewById(R.id.bannerNumber)
        bannerLogo = findViewById(R.id.bannerLogo)
        bannerName = findViewById(R.id.bannerName)
        bannerInfo = findViewById(R.id.bannerInfo)
        numberEntry = findViewById(R.id.numberEntry)
        guide = findViewById(R.id.guide)
        guidePanel = findViewById(R.id.guidePanel)
        guideCountry = findViewById(R.id.guideCountry)
        guideSearch = findViewById(R.id.guideSearch)
        guideSettings = findViewById(R.id.guideSettings)
        guideHeading = findViewById(R.id.guideHeading)
        guideEmpty = findViewById(R.id.guideEmpty)
        categoryList = findViewById(R.id.categoryList)
        channelList = findViewById(R.id.channelList)

        retryButton.setOnClickListener {
            if (channels.isEmpty()) loadChannels() else {
                hideMessage()
                skippedInRow = 0
                tune(current)
            }
        }
        messageCountryButton.setOnClickListener { openCountryPicker() }
        messageSettingsButton.setOnClickListener { openSettings() }
        guideCountry.setOnClickListener { openCountryPicker() }
        guideSettings.setOnClickListener { openSettings() }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        wasReady = true
                        retries = 0
                        handler.removeCallbacks(stallTask)
                        showLoading(null)
                        skippedInRow = 0
                        // Remembered only once it has shown a picture, so a dead channel never
                        // becomes the one the app opens on next time.
                        channels.getOrNull(current)?.let { ch ->
                            prefs.country?.let { prefs.setLastChannel(it, ch.key) }
                            openWatch(ch)
                        }
                    }
                    Player.STATE_BUFFERING -> if (wasReady) {
                        // Mid-programme hiccup: give it longer before giving up on this link.
                        handler.removeCallbacks(stallTask)
                        handler.postDelayed(stallTask, STALL_MS)
                    }
                    Player.STATE_ENDED -> tryNextAttempt()
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                // A stream that was playing a moment ago has most likely hit a short network
                // drop, not died. Give it a couple of goes before zapping away from it.
                if (wasReady && retries < MAX_RETRIES) {
                    retries++
                    handler.removeCallbacks(stallTask)
                    handler.removeCallbacks(retryTask)
                    showLoading(getString(R.string.tuning, channels[current].name))
                    handler.postDelayed(retryTask, RETRY_DELAY_MS)
                    return
                }
                tryNextAttempt()
            }
        })
        playerView.player = player
    }

    private fun setupGuide() {
        categoryAdapter = CategoryAdapter(
            onFocus = { cat ->
                if (guideSearch.text.isNotEmpty()) return@CategoryAdapter
                categoryTask?.let { handler.removeCallbacks(it) }
                categoryTask = Runnable { showCategory(cat.id) }.also { handler.postDelayed(it, 180) }
            },
            onClick = { cat ->
                clearSearch()
                showCategory(cat.id)
                focusChannel(0)
            },
        )
        channelAdapter = ChannelAdapter(
            isFavorite = { it.key in prefs.favorites() },
            onClick = { ch ->
                hideGuide()
                skippedInRow = 0
                direction = 1
                tune(ch.number - 1)
            },
            onLongClick = { ch ->
                val added = prefs.toggleFavorite(ch.key)
                say(getString(if (added) R.string.favorite_added else R.string.favorite_removed, ch.name))
                categoryAdapter.submit(buildCategories())
                // Taking a channel out of Favourites while looking at that list removes its row.
                if (selectedCategory == FAVORITES && guideSearch.text.isEmpty()) showCategory(FAVORITES)
            },
        )
        categoryList.layoutManager = LinearLayoutManager(this)
        categoryList.adapter = categoryAdapter
        categoryList.itemAnimator = null
        channelList.layoutManager = LinearLayoutManager(this)
        channelList.adapter = channelAdapter
        channelList.itemAnimator = null

        guideSearch.doAfterTextChanged { applySearch(it?.toString().orEmpty()) }
        guideSearch.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            focusChannel(0)
            true
        }

        // Tapping the dimmed area next to the list closes it.
        guide.setOnClickListener { hideGuide() }
        guidePanel.setOnClickListener { }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (channels.isNotEmpty()) showGuide()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (abs(velocityY) > abs(velocityX) && abs(velocityY) > 800) {
                    zap(if (velocityY < 0) 1 else -1)
                    return true
                }
                return false
            }
        })
        playerView.setOnTouchListener { _, e -> detector.onTouchEvent(e) }
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    guide.isVisible && guideSearch.text.isNotEmpty() && guideSearch.hasFocus() -> clearSearch()
                    guide.isVisible -> hideGuide()
                    digits.isNotEmpty() -> clearDigits()
                    System.currentTimeMillis() - lastBackPress < 2_000 -> finish()
                    else -> {
                        lastBackPress = System.currentTimeMillis()
                        say(getString(R.string.press_back_again))
                    }
                }
            }
        })
    }

    // endregion

    // region channels

    private fun openSetup() {
        pickingCountry = true
        hideGuide()
        setup.launch(Intent(this, OnboardingActivity::class.java))
    }

    private fun openCountryPicker() {
        pickingCountry = true
        hideGuide()
        setup.launch(OnboardingActivity.edit(this, OnboardingActivity.STEP_COUNTRY))
    }

    private fun openSettings() {
        pickingCountry = true
        hideGuide()
        settings.launch(Intent(this, SettingsActivity::class.java))
    }

    /** Something changed in setup or settings: reload if the country moved, refresh the rest. */
    private fun applyPrefs() {
        val code = prefs.country ?: return openSetup()
        if (code != loadedCountry || channels.isEmpty()) {
            loadChannels()
            return
        }
        loadLanguages()
        refreshGuide()
        if (current >= 0) tune(current)
    }

    private fun loadChannels() {
        val code = prefs.country ?: return
        loadJob?.cancel()
        languageJob?.cancel()
        handler.removeCallbacks(stallTask)
        handler.removeCallbacks(retryTask)
        closeWatch()
        player.stop()
        hideMessage()
        hideGuide()
        banner.visibility = View.GONE
        showLoading(getString(R.string.loading_channels))

        loadJob = lifecycleScope.launch {
            val result = runCatching { repo.load(code) }
            if (!isActive) return@launch
            val list = result.getOrNull().orEmpty()
            if (list.isEmpty()) {
                channels = emptyList()
                showLoading(null)
                showMessage(getString(if (result.isFailure) R.string.error_offline else R.string.error_no_channels))
                return@launch
            }
            if (loadedCountry != code) selectedCategory = FOR_YOU
            loadedCountry = code
            channels = list
            channelLanguages = emptyMap()
            skippedInRow = 0
            current = -1
            previous = -1
            refreshGuide()
            loadLanguages()
            // Open on the last channel watched, or the best match from setup on a fresh start.
            val last = prefs.lastChannel(code)
            val start = channels.indexOfFirst { it.key == last }
                .takeIf { it >= 0 }
                ?: forYou.firstOrNull()?.let { channels.indexOf(it) }
                ?: 0
            tune(start)
        }
    }

    /** Tags channels with the languages chosen at setup, from iptv-org's language playlists. */
    private fun loadLanguages() {
        languageJob?.cancel()
        val wanted = prefs.languages.take(MAX_LANGUAGES)
        if (wanted.isEmpty()) {
            channelLanguages = emptyMap()
            return
        }
        languageJob = lifecycleScope.launch {
            val keys = channels.map { it.key }.toSet()
            val tags = HashMap<String, MutableSet<String>>()
            for (code in wanted) {
                val inLanguage = repo.loadLanguageKeys(code)
                if (!isActive) return@launch
                for (key in keys) if (key in inLanguage) tags.getOrPut(key) { HashSet() }.add(code)
            }
            channelLanguages = tags
            if (channels.isNotEmpty()) refreshGuide()
        }
    }

    private fun zap(step: Int) {
        if (channels.isEmpty()) return
        direction = step
        skippedInRow = 0
        val base = if (pending >= 0) pending else current
        pending = wrap(base + step)
        showBanner(channels[pending])
        handler.removeCallbacks(tunePendingTask)
        handler.postDelayed(tunePendingTask, 450)
    }

    private fun tune(index: Int) {
        if (channels.isEmpty()) return
        handler.removeCallbacks(tunePendingTask)
        pending = -1
        val i = wrap(index)
        if (i != current && current >= 0) previous = current
        current = i

        val ch = channels[i]
        if (ch.key != watchedKey) closeWatch()
        channelAdapter.playingKey = ch.key
        attempts = ch.streams.flatMap(::attemptsFor)
        attemptIndex = 0
        retries = 0
        hideMessage()
        showBanner(ch)
        playAttempt()
    }

    private fun attemptsFor(s: StreamSource): List<Attempt> {
        val u = s.url.lowercase()
        return when {
            "m3u8" in u -> listOf(Attempt(s, MimeTypes.APPLICATION_M3U8))
            ".mpd" in u -> listOf(Attempt(s, MimeTypes.APPLICATION_MPD))
            // No hint in the URL: try it as a plain stream first, then as HLS.
            else -> listOf(Attempt(s, null), Attempt(s, MimeTypes.APPLICATION_M3U8))
        }
    }

    private fun playAttempt() {
        // Home was pressed while this was in flight. onStart tunes again when the app comes back.
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val attempt = attempts.getOrNull(attemptIndex) ?: return skipChannel()
        val ch = channels[current]
        showLoading(getString(R.string.tuning, ch.name))
        wasReady = false
        handler.removeCallbacks(retryTask)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(attempt.stream.userAgent ?: DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        attempt.stream.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder()
            .setUri(attempt.stream.url)
            .apply { attempt.mimeType?.let { setMimeType(it) } }
            .build()
        // Media3 throws for a scheme it has no module for (rtsp, rtmp). Treat that as a dead link.
        val source = runCatching {
            DefaultMediaSourceFactory(this)
                .setDataSourceFactory(DefaultDataSource.Factory(this, http))
                .createMediaSource(item)
        }.getOrElse { return tryNextAttempt() }

        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        handler.removeCallbacks(stallTask)
        handler.postDelayed(stallTask, START_TIMEOUT_MS)
    }

    private fun tryNextAttempt() {
        handler.removeCallbacks(stallTask)
        handler.removeCallbacks(retryTask)
        if (current < 0 || channels.isEmpty()) return
        retries = 0
        attemptIndex++
        if (attemptIndex < attempts.size) playAttempt() else skipChannel()
    }

    private fun skipChannel() {
        player.stop()
        skippedInRow++
        if (skippedInRow >= minOf(channels.size, MAX_SKIPS)) {
            skippedInRow = 0
            showLoading(null)
            showMessage(getString(R.string.error_all_down))
            return
        }
        say(getString(R.string.channel_off_air, channels[current].name))
        tune(current + direction)
    }

    private fun wrap(index: Int) = ((index % channels.size) + channels.size) % channels.size

    // endregion

    // region watch history

    private fun openWatch(ch: Channel) {
        if (ch.key == watchedKey) return
        closeWatch()
        watchedKey = ch.key
        watchStart = System.currentTimeMillis()
        prefs.country?.let { prefs.recordWatch(it, ch.key, 0, newVisit = true) }
    }

    private fun closeWatch() {
        val key = watchedKey ?: return
        val seconds = (System.currentTimeMillis() - watchStart) / 1000
        watchedKey = null
        if (seconds >= MIN_WATCH_SECONDS) prefs.country?.let { prefs.recordWatch(it, key, seconds, newVisit = false) }
    }

    // endregion

    // region remote control

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH && channels.isNotEmpty() && !message.isVisible) {
            showGuide()
            guideSearch.requestFocus()
            return true
        }
        if (guide.isVisible || message.isVisible || channels.isEmpty()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> zap(1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> zap(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (digits.isNotEmpty()) jumpToNumber() else showGuide()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_BOOKMARK -> showGuide()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> showBanner(channels[current])
            KeyEvent.KEYCODE_LAST_CHANNEL -> if (previous >= 0) tune(previous)
            KeyEvent.KEYCODE_SETTINGS -> openSettings()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> addDigit(keyCode - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> addDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun addDigit(d: Int) {
        if (digits.length >= 4) digits = ""
        digits += d
        numberEntry.text = digits
        if (!numberEntry.isVisible) numberEntry.fadeIn(150)
        handler.removeCallbacks(digitsTask)
        handler.postDelayed(digitsTask, 1_800)
    }

    private fun jumpToNumber() {
        val n = digits.toIntOrNull()
        clearDigits()
        if (n == null || n < 1 || n > channels.size) {
            say(getString(R.string.no_such_channel))
            return
        }
        direction = 1
        skippedInRow = 0
        tune(n - 1)
    }

    private fun clearDigits() {
        digits = ""
        handler.removeCallbacks(digitsTask)
        numberEntry.visibility = View.GONE
    }

    // endregion

    // region overlays

    private fun showBanner(ch: Channel) {
        bannerNumber.text = ch.number.toString()
        bannerName.text = ch.name
        val langs = channelLanguages[ch.key].orEmpty().mapNotNull { languageNames[it] }
        bannerInfo.text = (ch.categories.map { Categories.label(it) } + langs).joinToString("  ·  ")
        bannerLogo.loadLogo(ch.logo)
        banner.fadeIn(220, 16f)
        handler.removeCallbacks(hideBannerTask)
        handler.postDelayed(hideBannerTask, 5_000)
    }

    private fun showLoading(text: String?) {
        if (text == null) loading.fadeOut(160) else {
            loadingText.text = text
            if (!loading.isVisible) loading.fadeIn(160)
        }
    }

    private fun showMessage(text: String) {
        hideGuide()
        messageText.text = text
        message.fadeIn(220)
        retryButton.requestFocus()
    }

    private fun hideMessage() {
        message.visibility = View.GONE
    }

    private fun buildCategories(): List<GuideCategory> {
        val favs = prefs.favorites()
        val history = prefs.country?.let { prefs.history(it) }.orEmpty()
        forYou = Recommendations.forYou(channels, prefs.profile, prefs.interests, prefs.languages, channelLanguages, favs, history)
        mostWatched = Recommendations.mostWatched(channels, history)

        val counts = LinkedHashMap<String, Int>()
        channels.flatMap { it.categories }
            .sortedBy(Categories::rank)
            .forEach { counts[it] = (counts[it] ?: 0) + 1 }

        return buildList {
            if (forYou.isNotEmpty()) add(GuideCategory(FOR_YOU, getString(R.string.cat_for_you), forYou.size, R.drawable.ic_sparkle))
            if (mostWatched.isNotEmpty()) add(GuideCategory(MOST_WATCHED, getString(R.string.cat_most_watched), mostWatched.size, R.drawable.ic_trending))
            val favCount = channels.count { it.key in favs }
            if (favCount > 0) add(GuideCategory(FAVORITES, getString(R.string.cat_favorites), favCount, R.drawable.ic_heart))
            add(GuideCategory(ALL, getString(R.string.cat_all), channels.size, R.drawable.ic_apps))
            prefs.languages.forEach { code ->
                val n = channelLanguages.count { code in it.value }
                if (n > 0) add(GuideCategory("$LANGUAGE$code", languageNames[code] ?: code, n, R.drawable.ic_language))
            }
            counts.forEach { (id, n) -> add(GuideCategory(id, Categories.label(id), n, Categories.icon(id))) }
        }
    }

    private fun refreshGuide() {
        val country = countries.firstOrNull { it.code == prefs.country }
        guideCountry.text = country?.label ?: getString(R.string.change_country)
        categoryAdapter.submit(buildCategories())
        if (guideSearch.text.isEmpty()) showCategory(selectedCategory)
    }

    private fun channelsIn(category: String): List<Channel> = when {
        category == FOR_YOU -> forYou
        category == MOST_WATCHED -> mostWatched
        category == ALL -> channels
        category == FAVORITES -> prefs.favorites().let { favs -> channels.filter { it.key in favs } }
        category.startsWith(LANGUAGE) -> {
            val code = category.removePrefix(LANGUAGE)
            channels.filter { code in channelLanguages[it.key].orEmpty() }
        }
        else -> channels.filter { category in it.categories }
    }

    private fun showCategory(category: String) {
        val list = channelsIn(category)
        val id = if (list.isEmpty()) ALL else category
        selectedCategory = id
        categoryAdapter.setSelected(id)
        channelAdapter.submit(if (list.isEmpty()) channels else list)
        guideHeading.text = categoryAdapter.labelOf(id)
        guideEmpty.visibility = View.GONE
        channelList.visibility = View.VISIBLE
    }

    /** Filters the channel list as the viewer types. Empty text goes back to the category. */
    private fun applySearch(query: String) {
        if (!guide.isVisible) return
        val q = query.trim()
        if (q.isEmpty()) {
            showCategory(selectedCategory)
            return
        }
        val hits = Recommendations.search(channels, q)
        categoryAdapter.setSelected(null)
        channelAdapter.submit(hits)
        guideHeading.text = resources.getQuantityString(R.plurals.search_results, hits.size, hits.size)
        guideEmpty.text = getString(R.string.search_empty, q)
        guideEmpty.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
        channelList.visibility = if (hits.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun clearSearch() {
        if (guideSearch.text.isNotEmpty()) guideSearch.setText("")
        hideKeyboard()
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(guideSearch.windowToken, 0)
    }

    private fun showGuide() {
        if (channels.isEmpty() || current < 0) return
        clearDigits()
        banner.visibility = View.GONE
        guideHiding = false
        guide.animate().cancel()
        categoryAdapter.submit(buildCategories())

        // Open on the channel that is playing, in the last used category if it is there.
        val playingKey = channels[current].key
        if (guideSearch.text.isNotEmpty()) guideSearch.setText("")
        showCategory(selectedCategory)
        var pos = channelAdapter.items.indexOfFirst { it.key == playingKey }
        if (pos < 0) {
            showCategory(ALL)
            pos = current
        }
        if (!guide.isVisible) {
            guide.alpha = 0f
            guidePanel.translationX = -48f * resources.displayMetrics.density
            guide.visibility = View.VISIBLE
            guide.animate().alpha(1f).setDuration(200).start()
            guidePanel.animate().translationX(0f).setDuration(260).start()
            channelList.riseIn()
        }
        focusChannel(pos)
    }

    private fun hideGuide() {
        if (!guide.isVisible || guideHiding) return
        guideHiding = true
        hideKeyboard()
        guide.animate().alpha(0f).setDuration(150).withEndAction {
            guide.visibility = View.GONE
            guide.alpha = 1f
            guideHiding = false
            if (guideSearch.text.isNotEmpty()) guideSearch.setText("")
        }.start()
        playerView.requestFocus()
    }

    private fun focusChannel(position: Int) {
        if (channelAdapter.itemCount == 0) return
        val p = position.coerceIn(0, channelAdapter.itemCount - 1)
        val lm = channelList.layoutManager as LinearLayoutManager
        channelList.post {
            lm.scrollToPositionWithOffset(p, channelList.height / 3)
            channelList.post {
                channelList.findViewHolderForAdapterPosition(p)?.itemView?.requestFocus()
                    ?: channelList.requestFocus()
            }
        }
    }

    private fun say(text: String) {
        toast?.cancel()
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT).also { it.show() }
    }

    // endregion

    private companion object {
        const val ALL = "_all"
        const val FAVORITES = "_favorites"
        const val FOR_YOU = "_for_you"
        const val MOST_WATCHED = "_most_watched"
        const val LANGUAGE = "_lang:"
        const val MAX_LANGUAGES = 3
        const val MIN_WATCH_SECONDS = 20L
        const val START_TIMEOUT_MS = 12_000L
        const val STALL_MS = 20_000L
        const val MAX_SKIPS = 12
        const val MAX_RETRIES = 2
        const val RETRY_DELAY_MS = 3_000L
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
