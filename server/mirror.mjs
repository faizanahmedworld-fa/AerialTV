#!/usr/bin/env node
// Builds health checked copies of the iptv-org country playlists for the app's MIRROR_URL.
//
//   node server/mirror.mjs                 every country in app/src/main/assets/countries.json
//   node server/mirror.mjs pk ae           just these
//   node server/mirror.mjs --out /var/www/aerialtv/lists pk
//
// Each stream is fetched with the playlist's own user agent and referrer. HLS links must
// return a playlist whose segments actually download; other links must return media bytes.
// Dead links are dropped. Geo-blocked links are kept and moved to the end of the file, so the
// app still has them for viewers inside the right country. Plain Node 18+, no packages.
//
// Each country also gets a <code>.web.m3u with only the streams a browser can play (https, HLS,
// no custom user agent or referrer). That one feeds the web player in web/watch/index.html.

import fs from 'node:fs'
import path from 'node:path'
import http from 'node:http'
import https from 'node:https'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SOURCE = 'https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/countries/%s.m3u'
// Same fallback the app's player uses when a playlist entry has no user agent.
const DEFAULT_UA = 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36'

const opts = parseArgs(process.argv.slice(2))

// ---------------------------------------------------------------- HTTP

class HttpError extends Error {
  constructor(code, message) { super(message); this.code = code }
}

/** GET with manual redirects. Reads at most maxBytes, then drops the connection. */
function get(url, { ua, ref, maxBytes, deadline }, hops = 0) {
  return new Promise((resolve, reject) => {
    let u
    try { u = new URL(url) } catch { return reject(new HttpError('BADURL', 'bad url')) }
    const lib = u.protocol === 'https:' ? https : u.protocol === 'http:' ? http : null
    if (!lib) return reject(new HttpError('BADURL', `unsupported ${u.protocol}`))

    const left = deadline - Date.now()
    if (left <= 0) return reject(new HttpError('TIMEOUT', 'timeout'))

    const headers = { 'User-Agent': ua || DEFAULT_UA, Accept: '*/*' }
    if (ref) headers.Referer = ref
    const req = lib.get(u, { headers, insecureHTTPParser: true, agent: false })
    const timer = setTimeout(() => req.destroy(new HttpError('TIMEOUT', 'timeout')), left)
    const done = (fn, v) => { clearTimeout(timer); fn(v) }

    req.on('error', (e) => done(reject, e))
    req.on('response', (res) => {
      const status = res.statusCode
      if (status >= 300 && status < 400 && res.headers.location) {
        res.resume()
        req.destroy()
        clearTimeout(timer)
        if (hops >= 5) return reject(new HttpError('REDIRECTS', 'too many redirects'))
        const next = new URL(res.headers.location, u).href
        return get(next, { ua, ref, maxBytes, deadline }, hops + 1).then(resolve, reject)
      }
      const chunks = []
      let size = 0
      const finish = () => {
        req.destroy()
        done(resolve, { status, url: u.href, type: res.headers['content-type'] || '', body: Buffer.concat(chunks) })
      }
      res.on('data', (c) => {
        chunks.push(c)
        size += c.length
        if (size >= maxBytes) finish()
      })
      res.on('end', finish)
      res.on('error', (e) => done(reject, e))
    })
  })
}

// ---------------------------------------------------------------- playlist parsing

/** Splits an iptv-org playlist into its header line and one block per stream. */
function parsePlaylist(text) {
  const lines = text.replace(/^﻿/, '').split(/\r?\n/)
  const header = lines[0]?.startsWith('#EXTM3U') ? lines[0].trim() : '#EXTM3U'
  const entries = []
  let cur = null
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue
    if (line.startsWith('#EXTINF')) {
      cur = { extinf: line, opts: [], ua: attr(line, 'http-user-agent'), ref: attr(line, 'http-referrer') }
    } else if (line.startsWith('#')) {
      if (!cur) continue
      if (line.startsWith('#EXTVLCOPT:')) cur.opts.push(line)
      if (line.startsWith('#EXTVLCOPT:http-user-agent=')) cur.ua = after(line, '=')
      if (line.startsWith('#EXTVLCOPT:http-referrer=')) cur.ref = after(line, '=')
    } else if (cur) {
      cur.url = line
      cur.geoTagged = /geo-blocked/i.test(displayName(cur.extinf))
      entries.push(cur)
      cur = null
    }
  }
  return { header, entries }
}

const attr = (line, name) => line.match(new RegExp(`${name}="([^"]*)"`))?.[1] || null
const after = (s, ch) => s.slice(s.indexOf(ch) + 1)

/** The channel name: everything after the first comma that is outside quotes. */
function displayName(extinf) {
  let q = false
  for (let i = 0; i < extinf.length; i++) {
    if (extinf[i] === '"') q = !q
    else if (extinf[i] === ',' && !q) return extinf.slice(i + 1)
  }
  return ''
}

// ---------------------------------------------------------------- probing

const looksHtml = (r) => /text\/html/i.test(r.type) || /^\s*<(!doctype|html)/i.test(r.body.subarray(0, 200).toString('latin1'))
const blockedStatus = (s) => s === 401 || s === 403 || s === 451
const goneStatus = (s) => s === 404 || s === 410

/** Lines of an HLS playlist that are URIs, resolved against the playlist's own URL. */
function playlistUris(text, base) {
  return text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith('#'))
    .map((l) => { try { return new URL(l, base).href } catch { return null } }).filter(Boolean)
}

/**
 * Returns { state, ms, why } where state is alive, blocked (401/403/451 from here, so likely a
 * geo or token wall) or dead. hard is set when the link is certainly gone (404, no such host).
 */
async function probe(e) {
  const start = Date.now()
  const deadline = start + opts.timeout
  const req = { ua: e.ua, ref: e.ref, deadline }
  const result = (state, why, hard = false) => ({ state, why, hard, ms: Date.now() - start })

  try {
    let r = await get(e.url, { ...req, maxBytes: 256 * 1024 })
    for (let depth = 0; ; depth++) {
      if (blockedStatus(r.status)) return result('blocked', `HTTP ${r.status}`)
      if (goneStatus(r.status)) return result('dead', `HTTP ${r.status}`, true)
      if (r.status < 200 || r.status >= 300) return result('dead', `HTTP ${r.status}`)
      if (r.body.length === 0) return result('dead', 'empty body')

      const text = r.body.toString('utf8').replace(/^﻿/, '').trimStart()
      if (!text.startsWith('#EXTM3U')) {
        if (depth > 0 && !looksHtml(r)) return result('alive', 'segment ok')
        if (looksHtml(r)) return result('dead', 'html page')
        if (/<MPD[\s>]/.test(text.slice(0, 2000))) return result('alive', 'dash manifest')
        return result('alive', 'media bytes')
      }
      if (depth >= 3) return result('dead', 'playlist loop')

      const uris = playlistUris(text, r.url)
      if (uris.length === 0) return result('dead', 'empty playlist')
      let next
      if (text.includes('#EXT-X-STREAM-INF')) {
        next = uris[0] // master playlist: check the first variant
        r = await get(next, { ...req, maxBytes: 256 * 1024 })
      } else {
        // Media playlist: fetch a recent segment. The oldest ones may already be gone on live edges.
        next = uris[Math.max(0, uris.length - 3)]
        r = await get(next, { ...req, maxBytes: 8 * 1024 })
      }
    }
  } catch (err) {
    const code = err.code || 'ERROR'
    const hard = code === 'ENOTFOUND' || code === 'ECONNREFUSED' || code === 'BADURL' || code === 'ERR_INVALID_URL'
    return result('dead', code === 'TIMEOUT' ? 'timeout' : code, hard)
  }
}

// Probes are shared across countries (many channels sit in several lists) and capped per host,
// because some servers refuse a burst of parallel connections and would look dead.
const probeCache = new Map()
const hostSlots = new Map()

function probeOnce(e) {
  const key = `${e.url}\n${e.ua || ''}\n${e.ref || ''}`
  if (!probeCache.has(key)) probeCache.set(key, withHostSlot(e.url, () => probe(e)))
  return probeCache.get(key)
}

async function withHostSlot(url, fn) {
  let host
  try { host = new URL(url).host } catch { host = url }
  const slot = hostSlots.get(host) ?? { active: 0, queue: [] }
  hostSlots.set(host, slot)
  if (slot.active >= opts.perHost) await new Promise((r) => slot.queue.push(r))
  slot.active++
  try { return await fn() } finally {
    slot.active--
    slot.queue.shift()?.()
  }
}

/** Runs fn over items with at most n in flight. */
async function pool(items, n, fn) {
  const out = new Array(items.length)
  let i = 0
  await Promise.all(Array.from({ length: Math.min(n, items.length) }, async () => {
    while (i < items.length) { const k = i++; out[k] = await fn(items[k], k) }
  }))
  return out
}

// ---------------------------------------------------------------- output

function render(header, entries) {
  const lines = [header]
  for (const e of entries) lines.push(e.extinf, ...e.opts, e.url)
  return lines.join('\n') + '\n'
}

/** Marks a stream geo-blocked in the name, the way iptv-org does, so the app tries it last. */
function tagGeo(e) {
  if (e.geoTagged) return e
  return { ...e, extinf: `${e.extinf} [Geo-blocked]` }
}

/**
 * True when a browser can play the stream: an HTTPS page can't load http:// media, Safari and
 * hls.js only speak HLS, and no browser lets a page set the User-Agent or Referer header.
 */
function browserSafe(e) {
  return /^https:\/\//i.test(e.url) && /\.m3u8(\?|#|$)/i.test(e.url) && !e.ua && !e.ref
}

function writeAtomic(file, text) {
  const tmp = `${file}.tmp`
  fs.writeFileSync(tmp, text)
  fs.renameSync(tmp, file)
}

// ---------------------------------------------------------------- main

async function runCountry(code) {
  let text
  try {
    const r = await get(SOURCE.replace('%s', code), { ua: 'AerialTV-mirror', maxBytes: 20 * 1024 * 1024, deadline: Date.now() + 60_000 })
    if (r.status !== 200) throw new Error(`HTTP ${r.status}`)
    text = r.body.toString('utf8')
  } catch (err) {
    return { code, error: `download failed: ${err.message}` }
  }

  const { header, entries } = parsePlaylist(text)
  let results = await pool(entries, opts.concurrency, probeOnce)

  // Second chance for soft failures: a stream that timed out once is often just slow to start.
  const retry = entries.map((e, i) => i).filter((i) => results[i].state === 'dead' && !results[i].hard)
  if (retry.length) {
    const again = await pool(retry, Math.max(4, opts.concurrency >> 2), (i) => withHostSlot(entries[i].url, () => probe(entries[i])))
    retry.forEach((i, k) => { if (again[k].state !== 'dead') results[i] = again[k] })
  }

  const alive = []
  const geo = []
  let dead = 0
  entries.forEach((e, i) => {
    const r = results[i]
    if (opts.verbose) console.log(`  ${r.state.padEnd(7)} ${String(r.ms).padStart(5)}ms  ${r.why.padEnd(14)} ${displayName(e.extinf)}  ${e.url}`)
    if (e.geoTagged) {
      // Probes run from the server's country, so a failure here says little. Only a link that is
      // certainly gone is dropped.
      if (r.hard) dead++
      else geo.push(e)
    } else if (r.state === 'alive') alive.push(e)
    else if (r.state === 'blocked') geo.push(tagGeo(e))
    else dead++
  })

  const kept = [...alive, ...geo]
  const web = kept.filter(browserSafe)
  const summary = { code, total: entries.length, alive: alive.length, geo: geo.length, dead, web: web.length }
  if (kept.length === 0) return { ...summary, error: 'no working streams, kept previous files' }
  writeAtomic(path.join(opts.out, `${code}.m3u`), render(header, kept))
  // The web list may be empty for a country whose streams are all http or need a user agent.
  // An empty list is the truth there, so it's written anyway and the page says so.
  writeAtomic(path.join(opts.out, `${code}.web.m3u`), render(header, web))
  return summary
}

async function main() {
  const countries = opts.countries.length
    ? opts.countries
    : JSON.parse(fs.readFileSync(path.join(ROOT, 'app/src/main/assets/countries.json'), 'utf8')).map((c) => c.code)
  fs.mkdirSync(opts.out, { recursive: true })

  const started = Date.now()
  console.log(`Checking ${countries.length} playlist(s), output in ${opts.out}`)
  const report = []
  // Countries run one after another; the streams inside each run in parallel.
  for (const code of countries) {
    const s = await runCountry(code.toLowerCase())
    report.push(s)
    if (s.error && s.total === undefined) console.log(`${code}: ${s.error}`)
    else console.log(`${code}: ${s.alive} alive, ${s.geo} geo-blocked kept, ${s.dead} dead (of ${s.total}), ${s.web} for the web player${s.error ? `, ${s.error}` : ''}`)
  }

  const ok = report.filter((s) => s.total !== undefined)
  const sum = (k) => ok.reduce((a, s) => a + s[k], 0)
  console.log(`Done in ${Math.round((Date.now() - started) / 1000)}s: ${sum('alive')} alive, ${sum('geo')} geo-blocked kept, ${sum('dead')} dead, ${sum('web')} for the web player, ${report.length - ok.length} playlist(s) failed`)

  writeAtomic(path.join(opts.out, 'status.json'), JSON.stringify({ updated: new Date().toISOString(), countries: report }, null, 2))
}

function parseArgs(argv) {
  const o = { countries: [], out: path.join(ROOT, 'server/out'), concurrency: 40, perHost: 4, timeout: 8000, verbose: false }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--out') o.out = path.resolve(argv[++i])
    else if (a === '--concurrency') o.concurrency = Number(argv[++i])
    else if (a === '--per-host') o.perHost = Number(argv[++i])
    else if (a === '--timeout') o.timeout = Number(argv[++i])
    else if (a === '-v' || a === '--verbose') o.verbose = true
    else if (a.startsWith('-')) { console.error(`Unknown option ${a}`); process.exit(2) }
    else o.countries.push(a)
  }
  return o
}

main().catch((err) => { console.error(err); process.exit(1) })
