// Aerial TV for Windows. The main process opens the window, downloads playlists, sets the
// headers some streams insist on, and checks GitHub for a newer installer.

const { app, BrowserWindow, ipcMain, session, shell } = require('electron')
const crypto = require('crypto')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { spawn } = require('child_process')

// Optional mirror of the iptv-org playlists on your own domain, e.g.
// 'https://aerialtv.tv/lists/%s.m3u'. Same setting as MIRROR_URL in the Android app.
const MIRROR_URL = ''

// Releases are read from here. Tags look like windows-v1.0.0.
const UPDATE_REPO = 'faizanahmedworld-fa/AerialTV'
const UPDATE_TAG_PREFIX = 'windows-v'

const DEFAULT_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36'
const CACHE_MS = 6 * 60 * 60 * 1000
const STAGGER_MS = 2500

const SOURCES = [
  MIRROR_URL,
  'https://iptv-org.github.io/iptv/countries/%s.m3u',
  'https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/countries/%s.m3u',
  'https://cdn.jsdelivr.net/gh/iptv-org/iptv@gh-pages/countries/%s.m3u',
].filter(Boolean)

let win = null
let streamHeaders = { userAgent: null, referrer: null }
let pendingUpdate = null

if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (!win) return
    if (win.isMinimized()) win.restore()
    win.focus()
  })
  app.whenReady().then(start)
}

app.on('window-all-closed', () => app.quit())

function start() {
  app.userAgentFallback = DEFAULT_USER_AGENT

  // Streams sometimes need a particular user agent or referrer. The page can't set those
  // itself, so they're added here for whichever stream is playing.
  session.defaultSession.webRequest.onBeforeSendHeaders({ urls: ['http://*/*', 'https://*/*'] }, (details, done) => {
    const headers = details.requestHeaders
    headers['User-Agent'] = streamHeaders.userAgent || DEFAULT_USER_AGENT
    if (streamHeaders.referrer) headers['Referer'] = streamHeaders.referrer
    done({ requestHeaders: headers })
  })

  win = new BrowserWindow({
    width: 1280,
    height: 760,
    minWidth: 640,
    minHeight: 400,
    backgroundColor: '#070D1F',
    title: 'Aerial TV',
    icon: path.join(__dirname, 'build', 'icon.ico'),
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      // Free streams live on thousands of servers and few of them send CORS headers.
      // The page only ever loads the app's own files, so this is safe to switch off.
      webSecurity: false,
      backgroundThrottling: false,
    },
  })
  win.removeMenu()
  win.once('ready-to-show', () => win.show())
  win.on('closed', () => { win = null })

  // Never navigate away from the app or open popups inside it.
  win.webContents.on('will-navigate', (e) => e.preventDefault())
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })
  win.on('enter-full-screen', () => send('fullscreen', true))
  win.on('leave-full-screen', () => send('fullscreen', false))

  win.loadFile(path.join(__dirname, 'renderer', 'index.html'))

  if (app.isPackaged) setTimeout(() => checkForUpdate(false).catch(() => {}), 8000)
}

function send(channel, value) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, value)
}

// region playlists

ipcMain.handle('countries', () => {
  const file = app.isPackaged
    ? path.join(process.resourcesPath, 'countries.json')
    : path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'countries.json')
  return JSON.parse(fs.readFileSync(file, 'utf8'))
})

ipcMain.handle('system-countries', () => {
  // Windows region setting first, then the region part of each preferred language.
  const codes = [app.getLocaleCountryCode()]
  for (const lang of app.getPreferredSystemLanguages()) {
    const m = /[-_]([A-Za-z]{2})(?:$|[-_])/.exec(lang)
    if (m) codes.push(m[1])
  }
  return codes.filter(Boolean).map((c) => c.toLowerCase())
})

ipcMain.handle('playlist', async (_e, country) => {
  if (!/^[a-z]{2,4}$/.test(country)) throw new Error('Bad country code')
  const dir = path.join(app.getPath('userData'), 'playlists')
  const cache = path.join(dir, `${country}.m3u`)
  let stat = null
  try { stat = fs.statSync(cache) } catch {}

  if (stat && Date.now() - stat.mtimeMs < CACHE_MS) {
    const text = fs.readFileSync(cache, 'utf8')
    if (text.includes('#EXTINF')) return text
  }

  const text = await downloadFirst(country)
  if (text) {
    fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(cache, text)
    return text
  }
  if (stat) return fs.readFileSync(cache, 'utf8')
  throw new Error('No playlist source reachable')
})

// Starts each source a moment after the previous one and takes whichever answers first.
// A blocked host can hang for 20 seconds before failing, and nobody should wait for that.
function downloadFirst(country) {
  return new Promise((resolve) => {
    const controllers = []
    let left = SOURCES.length
    let done = false
    SOURCES.forEach((pattern, i) => {
      const ctrl = new AbortController()
      controllers.push(ctrl)
      setTimeout(async () => {
        if (done) return
        const text = await fetchPlaylist(pattern.replace('%s', country), ctrl.signal)
        left--
        if (done) return
        if (text) {
          done = true
          controllers.forEach((c) => c.abort())
          resolve(text)
        } else if (left === 0) {
          resolve(null)
        }
      }, i * STAGGER_MS)
    })
  })
}

async function fetchPlaylist(url, signal) {
  const timeout = AbortSignal.timeout(25000)
  try {
    const res = await fetch(url, {
      signal: AbortSignal.any([signal, timeout]),
      headers: { 'User-Agent': `AerialTV/${app.getVersion()} (Windows)` },
    })
    if (!res.ok) return null
    const text = (await res.text()).replace(/^﻿/, '')
    return text.startsWith('#EXTM3U') && text.includes('#EXTINF') ? text : null
  } catch {
    return null
  }
}

ipcMain.on('stream-headers', (_e, headers) => {
  streamHeaders = { userAgent: headers?.userAgent || null, referrer: headers?.referrer || null }
})

ipcMain.on('fullscreen', (_e, value) => {
  if (win) win.setFullScreen(typeof value === 'boolean' ? value : !win.isFullScreen())
})

ipcMain.handle('version', () => app.getVersion())

// endregion

// region updates

ipcMain.handle('check-update', () => checkForUpdate(true))
ipcMain.handle('install-update', () => installUpdate())

async function checkForUpdate(manual) {
  const res = await fetch(`https://api.github.com/repos/${UPDATE_REPO}/releases?per_page=30`, {
    headers: { 'User-Agent': `AerialTV/${app.getVersion()} (Windows)`, Accept: 'application/vnd.github+json' },
    signal: AbortSignal.timeout(15000),
  })
  if (!res.ok) throw new Error(`GitHub answered ${res.status}`)
  const releases = await res.json()

  let best = null
  for (const r of releases) {
    if (r.draft || r.prerelease || !r.tag_name?.startsWith(UPDATE_TAG_PREFIX)) continue
    const version = r.tag_name.slice(UPDATE_TAG_PREFIX.length)
    const asset = r.assets?.find((a) => a.name.toLowerCase().endsWith('.exe'))
    if (!asset || compareVersions(version, app.getVersion()) <= 0) continue
    if (!best || compareVersions(version, best.version) > 0) best = { version, asset, notes: r.body || '' }
  }

  pendingUpdate = best
  const info = best ? { version: best.version, notes: best.notes } : null
  if (best && !manual) send('update-available', info)
  return info
}

async function installUpdate() {
  const update = pendingUpdate
  if (!update) throw new Error('No update waiting')
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aerialtv-update-'))
  const file = path.join(dir, 'Aerial TV Setup.exe')

  const res = await fetch(update.asset.browser_download_url, {
    headers: { 'User-Agent': `AerialTV/${app.getVersion()} (Windows)` },
  })
  if (!res.ok || !res.body) throw new Error(`Download failed (${res.status})`)

  const total = Number(res.headers.get('content-length')) || update.asset.size || 0
  const hash = crypto.createHash('sha256')
  const out = fs.createWriteStream(file)
  let got = 0
  let lastSent = 0
  for await (const chunk of res.body) {
    hash.update(chunk)
    got += chunk.length
    if (!out.write(chunk)) await new Promise((r) => out.once('drain', r))
    if (total && Date.now() - lastSent > 250) {
      lastSent = Date.now()
      send('update-progress', got / total)
    }
  }
  await new Promise((r, j) => out.end((err) => (err ? j(err) : r())))

  if (update.asset.size && got !== update.asset.size) throw new Error('Download was cut short')
  const expected = /^sha256:([0-9a-f]{64})$/.exec(update.asset.digest || '')
  if (expected && hash.digest('hex') !== expected[1]) throw new Error('Download is damaged')

  send('update-progress', 1)
  // Silent install, then the installer opens the new version on its own.
  spawn(file, ['--updated', '/S', '--force-run'], { detached: true, stdio: 'ignore' }).unref()
  setTimeout(() => app.quit(), 500)
  return true
}

function compareVersions(a, b) {
  const pa = String(a).split('.').map((n) => parseInt(n, 10) || 0)
  const pb = String(b).split('.').map((n) => parseInt(n, 10) || 0)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0)
    if (d) return d
  }
  return 0
}

// endregion
