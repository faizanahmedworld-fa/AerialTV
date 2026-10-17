// Reads iptv-org M3U playlists. Same rules as M3uParser.kt in the Android app: entries that
// share a tvg-id (SD, HD and backup feeds of the same channel) become one channel with
// several streams, so a dead link falls back quietly.

const CATEGORY_ORDER = [
  'general', 'news', 'entertainment', 'movies', 'series', 'sports', 'kids', 'animation',
  'family', 'music', 'documentary', 'comedy', 'lifestyle', 'cooking', 'travel', 'outdoor',
  'culture', 'education', 'science', 'business', 'religious', 'legislative', 'classic',
  'auto', 'weather', 'shop', 'public', 'relax', 'interactive', 'undefined',
]

function categoryRank(c) {
  const i = CATEGORY_ORDER.indexOf(c)
  return i < 0 ? CATEGORY_ORDER.length - 1 : i
}

function categoryLabel(c) {
  return c === 'undefined' ? 'Other' : c.charAt(0).toUpperCase() + c.slice(1)
}

// 720p first: sharp on a big screen and still smooth on an average home connection.
function qualityScore(q) {
  return q === 0 ? 150 : Math.abs(q - 720)
}

/** Splits '#EXTINF:-1 a="x, y",Name' at the first comma that is not inside quotes. */
function splitInfo(line) {
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (c === '"') inQuotes = !inQuotes
    else if (c === ',' && !inQuotes) return [line.slice(0, i), line.slice(i + 1)]
  }
  return [line, '']
}

function parseM3u(text) {
  const byKey = new Map()
  let info = null
  let userAgent = null
  let referrer = null

  for (const raw of text.replace(/^﻿/, '').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line) continue
    if (line.startsWith('#EXTINF')) {
      info = line
      userAgent = null
      referrer = null
    } else if (line.startsWith('#EXTVLCOPT:http-user-agent=')) {
      userAgent = line.slice(line.indexOf('=') + 1)
    } else if (line.startsWith('#EXTVLCOPT:http-referrer=')) {
      referrer = line.slice(line.indexOf('=') + 1)
    } else if (!line.startsWith('#') && info) {
      add(byKey, info, line, userAgent, referrer)
      info = null
    }
  }

  return [...byKey.values()]
    .filter((b) => b.streams.length)
    .map((b) => ({
      key: b.key,
      name: b.name,
      logo: b.logo,
      categories: [...b.categories].sort((a, c) => categoryRank(a) - categoryRank(c)),
      streams: b.streams.sort((a, c) => (a.geoBlocked - c.geoBlocked) || (qualityScore(a.quality) - qualityScore(c.quality))),
    }))
    .sort((a, b) => (categoryRank(a.categories[0]) - categoryRank(b.categories[0])) ||
      (a.name.toLowerCase() < b.name.toLowerCase() ? -1 : a.name.toLowerCase() > b.name.toLowerCase() ? 1 : 0))
    .map((c, i) => ({ ...c, number: i + 1 }))
}

function add(byKey, header, url, ua, ref) {
  const [attrPart, namePart] = splitInfo(header)
  const attrs = {}
  for (const m of attrPart.matchAll(/([A-Za-z0-9-]+)="([^"]*)"/g)) attrs[m[1]] = m[2]

  const rawName = namePart.trim()
  const qm = /\((\d{3,4})p\)/.exec(rawName)
  const quality = qm ? parseInt(qm[1], 10) : 0
  const geoBlocked = /geo-blocked/i.test(rawName)
  const name = rawName.replace(/\((\d{3,4})p\)/g, '').replace(/\[[^\]]*]/g, '').replace(/\s+/g, ' ').trim() || rawName

  const tvgId = (attrs['tvg-id'] || '').split('@')[0].trim().toLowerCase()
  const key = tvgId || name.toLowerCase()

  let b = byKey.get(key)
  if (!b) {
    b = { key, name, logo: null, categories: new Set(), streams: [] }
    byKey.set(key, b)
  }
  if (name.length < b.name.length) b.name = name
  if (!b.logo && attrs['tvg-logo']) b.logo = attrs['tvg-logo']

  const groups = (attrs['group-title'] || '').split(';').map((g) => g.trim().toLowerCase()).filter(Boolean)
  for (const g of groups.length ? groups : ['undefined']) b.categories.add(g)

  if (!b.streams.some((s) => s.url === url)) {
    b.streams.push({
      url,
      userAgent: ua || attrs['http-user-agent'] || null,
      referrer: ref || attrs['http-referrer'] || null,
      quality,
      geoBlocked,
    })
  }
}
