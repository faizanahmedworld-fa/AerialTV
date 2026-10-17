// The whole app is one screen that behaves like a TV: it opens straight onto a channel,
// up/down changes channel, Enter opens the channel list. Dead streams are skipped on their own.
// This follows MainActivity.kt in the Android app as closely as a PC allows.

const ALL = '_all'
const FAVORITES = '_favorites'
const START_TIMEOUT_MS = 12000
const STALL_MS = 20000
const MAX_SKIPS = 12

const $ = (id) => document.getElementById(id)
const video = $('video')

let countries = []
let loadedCountry = null
let channels = []
let current = -1
let previous = -1
let pending = -1
let direction = 1
let attempts = []
let attemptIndex = 0
let wasReady = false
let skippedInRow = 0
let selectedCategory = ALL
let digits = ''
let playToken = 0
let loadToken = 0
let hls = null
let ts = null

const timers = {}
function later(name, ms, fn) {
  clearTimeout(timers[name])
  timers[name] = setTimeout(fn, ms)
}
function cancel(name) {
  clearTimeout(timers[name])
}

// region saved settings

const store = {
  get(key, fallback = null) {
    try {
      const v = localStorage.getItem(key)
      return v === null ? fallback : JSON.parse(v)
    } catch {
      return fallback
    }
  },
  set(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)) } catch {}
  },
}

const prefs = {
  get country() { return store.get('country') },
  set country(v) { store.set('country', v) },
  lastChannel: (country) => store.get(`last_${country}`),
  setLastChannel: (country, key) => store.set(`last_${country}`, key),
  favorites: () => new Set(store.get('favorites', [])),
  toggleFavorite(key) {
    const set = prefs.favorites()
    const added = !set.has(key)
    if (added) set.add(key)
    else set.delete(key)
    store.set('favorites', [...set])
    return added
  },
}

// endregion

// region start up

async function init() {
  countries = await aerial.countries()
  $('version').textContent = `Version ${await aerial.version()}`

  video.volume = store.get('volume', 1)
  video.muted = store.get('muted', false)
  updateVolumeUi()

  bindUi()
  if (prefs.country) loadChannels()
  else openPicker()
}

function bindUi() {
  $('retryButton').onclick = () => {
    if (!channels.length) return loadChannels()
    hideMessage()
    skippedInRow = 0
    tune(current)
  }
  $('messageCountryButton').onclick = openPicker
  $('guideCountry').onclick = openPicker
  $('guide').querySelector('.guideShade').onclick = hideGuide

  $('ctlUp').onclick = () => zap(1)
  $('ctlDown').onclick = () => zap(-1)
  $('ctlGuide').onclick = showGuide
  $('ctlFull').onclick = () => aerial.toggleFullscreen()
  $('ctlMute').onclick = toggleMute
  $('ctlVolume').oninput = (e) => setVolume(e.target.value / 100)
  // Keep keyboard focus off the mouse controls, so Enter and the arrows still work the TV.
  for (const b of $('controls').querySelectorAll('button')) b.onmousedown = (e) => e.preventDefault()

  // Single click opens the channel list, like a tap on a phone. Double click is full screen.
  video.addEventListener('click', () => later('click', 250, () => { if (channels.length) showGuide() }))
  video.addEventListener('dblclick', () => {
    cancel('click')
    aerial.toggleFullscreen()
  })
  video.addEventListener('wheel', (e) => {
    setVolume(video.volume + (e.deltaY < 0 ? 0.05 : -0.05))
    say(`Volume ${Math.round(video.volume * 100)}%`)
  }, { passive: true })

  document.addEventListener('mousemove', wake)
  document.addEventListener('keydown', onKey)

  $('countrySearch').addEventListener('input', renderCountries)
  $('countrySearch').addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown' || e.key === 'Enter') {
      e.preventDefault()
      const first = $('countryList').querySelector('.country')
      if (!first) return
      if (e.key === 'Enter') first.click()
      else first.focus()
    }
  })

  $('checkUpdate').onclick = (e) => {
    e.preventDefault()
    checkForUpdate()
  }
  $('updateNow').onclick = installUpdate
  $('updateLater').onclick = () => $('update').classList.add('hidden')
  aerial.onUpdateAvailable(showUpdate)
  aerial.onUpdateProgress((p) => {
    $('updateBar').style.width = `${Math.round(p * 100)}%`
    $('updateText').textContent = p >= 1 ? 'Installing. Aerial TV will open again in a moment.' : `Downloading the update… ${Math.round(p * 100)}%`
  })

  video.addEventListener('playing', () => {
    wasReady = true
    cancel('stall')
    showLoading(null)
    skippedInRow = 0
  })
  video.addEventListener('waiting', () => {
    // Mid-programme hiccup: give it longer before giving up on this link.
    if (wasReady) later('stall', STALL_MS, () => tryNextAttempt(playToken))
  })
  video.addEventListener('ended', () => tryNextAttempt(playToken))
}

function wake() {
  document.body.classList.remove('idle')
  later('idle', 3000, () => document.body.classList.add('idle'))
}

// endregion

// region channels

async function loadChannels() {
  const code = prefs.country
  if (!code) return
  const token = ++loadToken
  stopPlayback()
  hideMessage()
  hideGuide()
  $('banner').classList.add('hidden')
  showLoading('Getting your channels ready…')

  let list = []
  let failed = false
  try {
    list = parseM3u(await aerial.playlist(code))
  } catch {
    failed = true
  }
  if (token !== loadToken) return

  if (!list.length) {
    channels = []
    showLoading(null)
    showMessage(failed
      ? "Can't reach the internet. Check your WiFi or cable, then press Try again."
      : "We couldn't find free channels for this country yet. Try another country.")
    return
  }
  if (loadedCountry !== code) selectedCategory = ALL
  loadedCountry = code
  channels = list
  skippedInRow = 0
  current = -1
  previous = -1
  const last = prefs.lastChannel(code)
  tune(Math.max(0, channels.findIndex((c) => c.key === last)))
}

function zap(step) {
  if (!channels.length || !$('message').classList.contains('hidden')) return
  direction = step
  skippedInRow = 0
  const base = pending >= 0 ? pending : current
  pending = wrap(base + step)
  showBanner(channels[pending])
  later('tune', 450, () => { if (pending >= 0) tune(pending) })
}

function tune(index) {
  if (!channels.length) return
  cancel('tune')
  pending = -1
  const i = wrap(index)
  if (i !== current && current >= 0) previous = current
  current = i

  const ch = channels[i]
  prefs.setLastChannel(prefs.country, ch.key)
  attempts = ch.streams.flatMap(attemptsFor)
  attemptIndex = 0
  hideMessage()
  showBanner(ch)
  markPlaying()
  playAttempt()
}

function attemptsFor(s) {
  const u = s.url.toLowerCase()
  if (u.includes('m3u8')) return [{ stream: s, kind: 'hls' }]
  if (u.includes('.mpd')) return [{ stream: s, kind: 'native' }]
  if (/\.(ts|flv)(\?|$)/.test(u)) return [{ stream: s, kind: 'ts' }, { stream: s, kind: 'hls' }]
  // No hint in the URL: try it as a plain stream first, then as HLS, then as raw MPEG-TS.
  return [{ stream: s, kind: 'native' }, { stream: s, kind: 'hls' }, { stream: s, kind: 'ts' }]
}

function stopPlayback() {
  playToken++
  cancel('stall')
  cancel('tune')
  if (hls) {
    hls.destroy()
    hls = null
  }
  if (ts) {
    try { ts.destroy() } catch {}
    ts = null
  }
  video.onerror = null
  video.removeAttribute('src')
  video.load()
}

function playAttempt() {
  const attempt = attempts[attemptIndex]
  if (!attempt) return skipChannel()
  stopPlayback()
  const token = playToken
  const fail = () => tryNextAttempt(token)
  const { url, userAgent, referrer } = attempt.stream

  showLoading(`Tuning in to ${channels[current].name}…`)
  wasReady = false
  aerial.setStreamHeaders({ userAgent, referrer })
  later('stall', START_TIMEOUT_MS, fail)
  video.onerror = fail

  if (attempt.kind === 'hls') {
    if (!window.Hls || !Hls.isSupported()) return fail()
    hls = new Hls({
      capLevelToPlayerSize: true,
      manifestLoadingMaxRetry: 1,
      levelLoadingMaxRetry: 2,
      fragLoadingMaxRetry: 3,
    })
    let recovered = false
    hls.on(Hls.Events.MANIFEST_PARSED, () => video.play().catch(() => {}))
    hls.on(Hls.Events.ERROR, (_e, data) => {
      if (!data.fatal || token !== playToken) return
      if (data.type === Hls.ErrorTypes.MEDIA_ERROR && !recovered) {
        recovered = true
        hls.recoverMediaError()
        return
      }
      fail()
    })
    hls.loadSource(url)
    hls.attachMedia(video)
  } else if (attempt.kind === 'ts') {
    if (!window.mpegts || !mpegts.getFeatureList().mseLivePlayback) return fail()
    ts = mpegts.createPlayer({ type: url.toLowerCase().includes('.flv') ? 'flv' : 'mpegts', isLive: true, url },
      { enableWorker: true, liveBufferLatencyChasing: true })
    ts.on(mpegts.Events.ERROR, fail)
    ts.attachMediaElement(video)
    ts.load()
    video.play().catch(() => {})
  } else {
    video.src = url
    video.play().catch(() => {})
  }
}

function tryNextAttempt(token) {
  if (token !== playToken) return
  cancel('stall')
  if (current < 0 || !channels.length) return
  attemptIndex++
  if (attemptIndex < attempts.length) playAttempt()
  else skipChannel()
}

function skipChannel() {
  stopPlayback()
  skippedInRow++
  if (skippedInRow >= Math.min(channels.length, MAX_SKIPS)) {
    skippedInRow = 0
    showLoading(null)
    showMessage("These channels aren't answering right now. Check your internet and press Try again.")
    return
  }
  say(`${channels[current].name} is off air right now. Trying the next channel.`)
  tune(current + direction)
}

function wrap(index) {
  return ((index % channels.length) + channels.length) % channels.length
}

// endregion

// region keyboard

function onKey(e) {
  if (e.key === 'F11') {
    e.preventDefault()
    return aerial.toggleFullscreen()
  }
  if (e.ctrlKey || e.altKey || e.metaKey) return

  if (!$('picker').classList.contains('hidden')) return pickerKey(e)
  if (!$('guide').classList.contains('hidden')) return guideKey(e)
  if (!$('message').classList.contains('hidden')) {
    if (e.key === 'Escape') aerial.toggleFullscreen(false)
    return
  }
  if (!channels.length) return

  const k = e.key
  let handled = true
  if (k === 'ArrowUp' || k === 'PageUp' || k === 'ChannelUp') zap(1)
  else if (k === 'ArrowDown' || k === 'PageDown' || k === 'ChannelDown') zap(-1)
  else if (k === 'Enter') digits ? jumpToNumber() : showGuide()
  else if (k === 'ArrowLeft' || k === 'ContextMenu' || k === 'l' || k === 'L' || k === 'g' || k === 'G') showGuide()
  else if (k === 'ArrowRight' || k === 'i' || k === 'I') showBanner(channels[current])
  else if (k === 'Backspace') digits ? clearDigits() : previous >= 0 && tune(previous)
  else if (k === 'f' || k === 'F') aerial.toggleFullscreen()
  else if (k === 'm' || k === 'M') toggleMute()
  else if (k === 'Escape') digits ? clearDigits() : aerial.toggleFullscreen(false)
  else if (/^[0-9]$/.test(k)) addDigit(k)
  else handled = false
  if (handled) e.preventDefault()
}

function addDigit(d) {
  if (digits.length >= 4) digits = ''
  digits += d
  $('numberEntry').textContent = digits
  $('numberEntry').classList.remove('hidden')
  later('digits', 1800, jumpToNumber)
}

function jumpToNumber() {
  const n = parseInt(digits, 10)
  clearDigits()
  if (!n || n < 1 || n > channels.length) return say("There's no channel with that number")
  direction = 1
  skippedInRow = 0
  tune(n - 1)
}

function clearDigits() {
  digits = ''
  cancel('digits')
  $('numberEntry').classList.add('hidden')
}

// endregion

// region overlays

function showBanner(ch) {
  if (!$('guide').classList.contains('hidden')) return
  $('bannerNumber').textContent = ch.number
  $('bannerName').textContent = ch.name
  $('bannerInfo').textContent = ch.categories.map(categoryLabel).join('  ·  ')
  const logo = $('bannerLogo')
  logo.onerror = () => logo.classList.add('hidden')
  if (ch.logo) {
    logo.classList.remove('hidden')
    logo.src = ch.logo
  } else {
    logo.classList.add('hidden')
    logo.removeAttribute('src')
  }
  $('banner').classList.remove('hidden')
  later('banner', 5000, () => $('banner').classList.add('hidden'))
}

function showLoading(text) {
  $('loading').classList.toggle('hidden', text === null)
  $('loadingText').textContent = text || ''
}

function showMessage(text) {
  hideGuide()
  $('messageText').textContent = text
  $('message').classList.remove('hidden')
  $('retryButton').focus()
}

function hideMessage() {
  $('message').classList.add('hidden')
}

function say(text) {
  const t = $('toast')
  t.textContent = text
  t.classList.remove('hidden')
  later('toast', 2500, () => t.classList.add('hidden'))
}

function setVolume(v) {
  video.volume = Math.min(1, Math.max(0, v))
  if (video.volume > 0) video.muted = false
  store.set('volume', video.volume)
  store.set('muted', video.muted)
  updateVolumeUi()
}

function toggleMute() {
  video.muted = !video.muted
  store.set('muted', video.muted)
  updateVolumeUi()
  say(video.muted ? 'Sound off' : 'Sound on')
}

function updateVolumeUi() {
  $('ctlVolume').value = Math.round(video.muted ? 0 : video.volume * 100)
  $('ctlMute').classList.toggle('muted', video.muted || video.volume === 0)
}

// endregion

// region channel list

function buildCategories() {
  const favs = prefs.favorites()
  const counts = new Map()
  channels.flatMap((c) => c.categories)
    .sort((a, b) => categoryRank(a) - categoryRank(b))
    .forEach((c) => counts.set(c, (counts.get(c) || 0) + 1))

  const out = []
  const favCount = channels.filter((c) => favs.has(c.key)).length
  if (favCount) out.push({ id: FAVORITES, label: '♥  Favourites', count: favCount })
  out.push({ id: ALL, label: 'All channels', count: channels.length })
  counts.forEach((n, id) => out.push({ id, label: categoryLabel(id), count: n }))
  return out
}

function channelsIn(category) {
  if (category === ALL) return channels
  if (category === FAVORITES) {
    const favs = prefs.favorites()
    return channels.filter((c) => favs.has(c.key))
  }
  return channels.filter((c) => c.categories.includes(category))
}

function renderCategories() {
  const box = $('categoryList')
  box.textContent = ''
  for (const cat of buildCategories()) {
    const b = document.createElement('button')
    b.className = 'cat'
    b.dataset.id = cat.id
    b.innerHTML = '<span class="label"></span><span class="count"></span>'
    b.querySelector('.label').textContent = cat.label
    b.querySelector('.count').textContent = cat.count
    b.onclick = () => showCategory(cat.id)
    b.onfocus = () => later('category', 180, () => showCategory(cat.id))
    box.appendChild(b)
  }
}

function showCategory(category) {
  let list = channelsIn(category)
  const id = list.length ? category : ALL
  if (!list.length) list = channels
  selectedCategory = id
  for (const b of $('categoryList').children) b.classList.toggle('selected', b.dataset.id === id)
  renderChannels(list)
}

function renderChannels(list) {
  const box = $('channelList')
  const favs = prefs.favorites()
  const playingKey = current >= 0 ? channels[current].key : null
  box.textContent = ''
  box.scrollTop = 0
  const frag = document.createDocumentFragment()
  for (const ch of list) {
    const b = document.createElement('button')
    b.className = 'chan'
    b.dataset.key = ch.key
    b.classList.toggle('playing', ch.key === playingKey)
    b.innerHTML = '<span class="num"></span><span class="noLogo"></span><span class="name"></span><span class="fav" title="Favourite">♥</span>'
    b.querySelector('.num').textContent = ch.number
    b.querySelector('.name').textContent = ch.name
    if (ch.logo) {
      const img = document.createElement('img')
      img.loading = 'lazy'
      img.alt = ''
      img.src = ch.logo
      img.onerror = () => img.replaceWith(Object.assign(document.createElement('span'), { className: 'noLogo' }))
      b.querySelector('.noLogo').replaceWith(img)
    }
    const fav = b.querySelector('.fav')
    fav.classList.toggle('on', favs.has(ch.key))
    fav.onclick = (e) => {
      e.stopPropagation()
      toggleFavorite(ch)
    }
    b.onclick = () => {
      hideGuide()
      skippedInRow = 0
      direction = 1
      tune(ch.number - 1)
    }
    b.oncontextmenu = (e) => {
      e.preventDefault()
      toggleFavorite(ch)
    }
    frag.appendChild(b)
  }
  box.appendChild(frag)
}

function toggleFavorite(ch) {
  const added = prefs.toggleFavorite(ch.key)
  say(added ? `${ch.name} added to Favourites` : `${ch.name} removed from Favourites`)
  const row = $('channelList').querySelector(`.chan[data-key="${CSS.escape(ch.key)}"] .fav`)
  if (row) row.classList.toggle('on', added)
  const focusedCat = document.activeElement?.closest?.('.cat')?.dataset.id
  renderCategories()
  for (const b of $('categoryList').children) b.classList.toggle('selected', b.dataset.id === selectedCategory)
  if (focusedCat) $('categoryList').querySelector(`[data-id="${CSS.escape(focusedCat)}"]`)?.focus()
}

function markPlaying() {
  const key = channels[current]?.key
  for (const b of $('channelList').children) b.classList.toggle('playing', b.dataset.key === key)
}

function showGuide() {
  if (!channels.length || current < 0) return
  clearDigits()
  cancel('click')
  $('banner').classList.add('hidden')
  $('guideCountry').textContent = countryLabel(countries.find((c) => c.code === prefs.country)) || 'Change country'
  renderCategories()

  // Open on the channel that is playing, in the last used category if it is there.
  const playingKey = channels[current].key
  showCategory(selectedCategory)
  if (!channelsIn(selectedCategory).some((c) => c.key === playingKey)) showCategory(ALL)
  $('guide').classList.remove('hidden')
  focusChannel(playingKey)
}

function hideGuide() {
  $('guide').classList.add('hidden')
  cancel('category')
}

function focusChannel(key) {
  const box = $('channelList')
  const row = (key && box.querySelector(`.chan[data-key="${CSS.escape(key)}"]`)) || box.querySelector('.chan')
  if (!row) return
  row.focus({ preventScroll: true })
  box.scrollTop = row.offsetTop - box.offsetTop - box.clientHeight / 3
}

function guideKey(e) {
  const el = document.activeElement
  const inCats = !!el?.closest?.('#categoryList')
  const inChans = !!el?.closest?.('#channelList')
  const k = e.key
  let handled = true

  if (k === 'Escape' || k === 'Backspace') hideGuide()
  else if (k === 'ArrowDown' || k === 'ArrowUp') {
    if (!inCats && !inChans) focusChannel(channels[current]?.key)
    else {
      const next = k === 'ArrowDown' ? el.nextElementSibling : el.previousElementSibling
      if (next) next.focus()
    }
  } else if (k === 'PageDown' || k === 'PageUp') {
    if (inCats || inChans) {
      const items = [...el.parentElement.children]
      const step = Math.max(1, Math.floor(el.parentElement.clientHeight / el.offsetHeight) - 1)
      const i = items.indexOf(el) + (k === 'PageDown' ? step : -step)
      items[Math.max(0, Math.min(items.length - 1, i))].focus()
    }
  } else if (k === 'ArrowRight' && inCats) {
    showCategory(el.dataset.id)
    focusChannel(channels[current]?.key)
  } else if (k === 'ArrowLeft' && inChans) {
    $('categoryList').querySelector('.cat.selected')?.focus()
  } else if ((k === 'f' || k === 'F' || k === '*') && inChans) {
    const ch = channels.find((c) => c.key === el.dataset.key)
    if (ch) toggleFavorite(ch)
  } else handled = false
  if (handled) e.preventDefault()
}

// endregion

// region country picker

function countryLabel(c) {
  return c ? (c.flag ? `${c.flag}  ${c.name}` : c.name) : ''
}

async function openPicker() {
  hideGuide()
  stopPlayback()
  showLoading(null)
  $('banner').classList.add('hidden')
  $('picker').classList.remove('hidden')
  $('countrySearch').value = ''

  // Likely countries, best first: the Windows region, then each preferred language.
  const guesses = (await aerial.systemCountries()).map((c) => (c === 'gb' ? 'uk' : c))
  const chosen = countries.find((c) => c.code === prefs.country)
  const detected = guesses.map((g) => countries.find((c) => c.code === g)).filter(Boolean)
  const intl = countries.find((c) => c.code === 'int')
  pickerSuggested = [...new Set([chosen, ...detected.slice(0, 2), intl].filter(Boolean))]
  renderCountries()
  const first = $('countryList').querySelector('.country')
  if (first) first.focus()
  else $('countrySearch').focus()
}

let pickerSuggested = []

function renderCountries() {
  const box = $('countryList')
  const q = $('countrySearch').value.trim().toLowerCase()
  box.textContent = ''

  const addHeader = (text) => {
    const h = document.createElement('div')
    h.className = 'listHeader'
    h.textContent = text
    box.appendChild(h)
  }
  const addRow = (c) => {
    const b = document.createElement('button')
    b.className = 'country'
    b.classList.toggle('selected', c.code === prefs.country)
    b.textContent = countryLabel(c)
    b.onclick = () => pickCountry(c)
    box.appendChild(b)
  }

  if (q) {
    const hits = countries.filter((c) => c.name.toLowerCase().includes(q) || c.code === q)
    hits.sort((a, b) => a.name.toLowerCase().startsWith(q) === b.name.toLowerCase().startsWith(q) ? 0 : a.name.toLowerCase().startsWith(q) ? -1 : 1)
    if (!hits.length) addHeader('No country matches that')
    hits.forEach(addRow)
    return
  }
  if (pickerSuggested.length) {
    addHeader('Suggested')
    pickerSuggested.forEach(addRow)
  }
  addHeader('All countries')
  countries.filter((c) => !pickerSuggested.includes(c)).forEach(addRow)
}

function pickCountry(c) {
  prefs.country = c.code
  $('picker').classList.add('hidden')
  if (c.code === loadedCountry && channels.length) tune(current)
  else loadChannels()
}

function pickerKey(e) {
  const el = document.activeElement
  const k = e.key
  if (k === 'Escape') {
    // First run has nowhere to go back to, so the picker stays.
    if (!prefs.country) return
    $('picker').classList.add('hidden')
    if (channels.length && current >= 0) tune(current)
    else loadChannels()
    e.preventDefault()
    return
  }
  if (!el?.classList.contains('country')) return
  if (k === 'ArrowDown' || k === 'ArrowUp') {
    e.preventDefault()
    let next = k === 'ArrowDown' ? el.nextElementSibling : el.previousElementSibling
    while (next && !next.classList.contains('country')) next = k === 'ArrowDown' ? next.nextElementSibling : next.previousElementSibling
    if (next) next.focus()
    else if (k === 'ArrowUp') $('countrySearch').focus()
  } else if (k.length === 1 && /\S/.test(k)) {
    // Typing anywhere in the list searches.
    $('countrySearch').focus()
  }
}

// endregion

// region updates

function showUpdate(info) {
  $('updateText').textContent = `Aerial TV ${info.version} is ready. It takes about a minute to install and the app opens again by itself.`
  $('updateBar').parentElement.classList.add('hidden')
  $('updateNow').disabled = false
  $('update').querySelector('.buttons').classList.remove('hidden')
  $('update').classList.remove('hidden')
}

async function checkForUpdate() {
  say('Checking for updates…')
  try {
    const info = await aerial.checkUpdate()
    if (info) showUpdate(info)
    else say('You have the latest version')
  } catch {
    say("Couldn't check for updates. Try again later.")
  }
}

async function installUpdate() {
  $('update').querySelector('.buttons').classList.add('hidden')
  $('updateBar').parentElement.classList.remove('hidden')
  $('updateBar').style.width = '0'
  $('updateText').textContent = 'Downloading the update…'
  try {
    await aerial.installUpdate()
  } catch (err) {
    $('updateText').textContent = `The update didn't download (${err.message.replace(/^.*Error: /, '')}). Try again later.`
    $('updateBar').parentElement.classList.add('hidden')
    $('update').querySelector('.buttons').classList.remove('hidden')
  }
}

// endregion

init()
