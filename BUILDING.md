# Building Aerial TV

This page is for whoever builds and releases the app. If you just want to watch TV, go back to the [main page](README.md).

Channels come from the public [iptv-org/iptv](https://github.com/iptv-org/iptv) playlists (11,000+ streams, CC0 licence). Aerial TV hosts no video.

## What the app does

* Opens on the last channel you watched, like a normal TV.
* Remote: ▲ ▼ or CH+ CH− change channel, OK or ◀ opens the list, ▶ shows what's on, number keys jump to a channel, Back twice closes.
* Phone: tap for the list, swipe up or down to change channel.
* Dead streams are skipped on their own. Each channel keeps all its backup links (SD, HD, mirrors) and tries them in turn, 12 seconds each, before moving to the next channel.
* Guesses the country from the mobile network, SIM, time zone, then device language. TV boxes usually ship in US English but with the right time zone, so the time zone does most of the work.
* Favourites: hold OK on a channel.
* Playlists come from iptv-org.github.io, GitHub raw and jsDelivr, started 2.5 seconds apart, and the first good answer wins. Some ISPs block github.io. The last good list is cached for 6 hours and used offline.
* Android 5.0 and up. APK is about 2 MB.

## Project layout

| Path | What's in it |
| --- | --- |
| `app/src/main/java/tv/aerialtv/app/MainActivity.kt` | The TV screen: player, remote keys, channel list, auto skip |
| `app/src/main/java/tv/aerialtv/app/CountryActivity.kt` | First run country picker |
| `app/src/main/java/tv/aerialtv/app/M3uParser.kt` | Reads iptv-org playlists and merges feeds of the same channel |
| `app/src/main/java/tv/aerialtv/app/ChannelRepository.kt` | Downloads and caches playlists |
| `app/src/main/java/tv/aerialtv/app/Prefs.kt` | Saved country, last channel, favourites, country detection |
| `app/src/main/assets/countries.json` | Countries that have a playlist, from iptv-org's `PLAYLISTS.md` |
| `app/src/test/` | Parser tests against real Pakistan and Ukraine playlists |
| `tools/icons.ps1` | Draws the launcher icons and the Android TV banner |
| `server/mirror.mjs` | Server job that builds health checked playlists for `MIRROR_URL`, plus browser safe copies for the web player |
| `web/index.html` | The landing page people find through Google: what the app is, install steps, a link per country, structured data. Plain HTML, no build step |
| `web/sitemap.xml`, `web/robots.txt`, `web/404.html`, `web/og.png`, `web/icon.png` | Search and sharing files for the landing page. The site address `https://faizanahmedworld-fa.github.io/AerialTV/` is written into the canonical links, Open Graph tags, JSON-LD and sitemap, so search and replace it when the site moves to its own domain |
| `web/watch/index.html` | The web player for iPhone, iPad (and AirPlay to Apple TV), Chrome and desktop browsers. One file, no build step |
| `web/countries.json` | Copy of `app/src/main/assets/countries.json` for the web player. Keep the two the same |
| `docs/Apple.md` | Why there's no iPhone or Apple TV version, and the web player route that gets round it |

## First time setup on a new PC

1. Install JDK 17 (Temurin from adoptium.net is fine).
2. Install Android Studio, or just the Android command line tools, and add **Android SDK Platform 35** and **Build Tools 35**.
3. Create `local.properties` in the project folder with one line pointing at the SDK, using forward slashes:

   ```
   sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
   ```

4. Put the signing key back: copy `aerialtv-release.jks` into a `signing` folder and `keystore.properties` into the project folder. Both come from your backup. Without them the build still works but makes an unsigned APK.

On Faizan's PC, JDK 17 and the SDK already live in `C:\Users\faiza\AndroidBuild`. Point `JAVA_HOME` there first, because the system Java is version 8:

```bash
export JAVA_HOME="/c/Users/faiza/AndroidBuild/jdk/jdk-17.0.20.1+1"
```

## Build

```bash
./gradlew assembleRelease
```

The signed APK lands in `app/build/outputs/apk/release/app-release.apk`.

Run the parser tests:

```bash
./gradlew testReleaseUnitTest
```

Try it on a connected TV box or phone with USB or network debugging switched on:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Releasing a new version

1. In `app/build.gradle.kts`, raise `versionCode` by 1 and set `versionName` (for example `1.1`). Android refuses an update whose `versionCode` isn't higher.
2. Build with `./gradlew assembleRelease`.
3. Copy the APK to `release/AerialTVAndroid.apk` and commit it. The name says Android because the one file covers Android TV, Fire TV and phones, and the Windows installer sits next to it on the releases page. That one file is tracked in git (the rest of the folder is ignored), so the backup link on the main page always serves the current version. Keep the exact name, because the download button points at it too.
4. Publish it on GitHub:

   ```bash
   gh release create v1.1 release/AerialTVAndroid.apk --title "Aerial TV for Android 1.1" --notes "What changed"
   ```

5. Upload the same file to your website's download link.

## Signing key (back this up)

`signing/aerialtv-release.jks` and `keystore.properties` sign every release. Copy both somewhere safe (a USB stick and a password manager). If they're lost, people can't update the app and have to uninstall and reinstall. Neither file goes in git.

## Changing things

* App name: `app_name` in `app/src/main/res/values/strings.xml`, plus the banner text in `tools/icons.ps1`. Rerun the script after changing it:

  ```bash
  powershell -ExecutionPolicy Bypass -File tools/icons.ps1 -Res app/src/main/res
  ```

* Package id: `applicationId` in `app/build.gradle.kts` (currently `tv.aerialtv.app`). Settle this before the first public release. It can't change later without breaking updates.
* Your own playlist mirror: set `MIRROR_URL` in `app/build.gradle.kts`, for example `https://aerialtv.tv/lists/%s.m3u`. It's tried before the iptv-org hosts. See "Playlist mirror" below for the job that fills it.
* Country list: `app/src/main/assets/countries.json`.

## Web player

`web/watch/index.html` is Aerial TV for browsers: iPhone and iPad in Safari (and from there to an Apple TV with AirPlay), Chrome on Android, and any laptop. Why it exists and what it can't do is in [docs/Apple.md](docs/Apple.md). It's a single HTML file with the CSS and JavaScript inside, no framework and no build step. Safari plays HLS natively; every other browser loads [hls.js](https://github.com/video-dev/hls.js) from jsDelivr.

What it does: shows the country picker on first visit and remembers the choice and the last channel in `localStorage`, numbers and groups the channels the way the app does (same merging of SD, HD and backup feeds), changes channel with swipe up or down, the arrow keys or Page Up and Page Down, opens the list on a tap, Enter or the left arrow, jumps to a number with the digit keys, and skips a stream that shows no picture within 12 seconds, trying the channel's other feeds first. The AirPlay button appears when Safari finds a receiver on the network.

Try it locally with any static server, for example:

```bash
python -m http.server 8791 --directory web
```

Then open `http://localhost:8791/watch/`. Add `?country=pk` to the address to force a country.

It's live at `https://faizanahmedworld-fa.github.io/AerialTV/`. The "Web player" job in `.github/workflows/pages.yml` publishes the `web` folder to GitHub Pages on every push to `main` that touches it, and fails on purpose if `web/countries.json` has drifted from the app's copy. Some internet providers block github.io, so the domain is still the end goal. To put it on the domain, copy the `web` folder to the web root (`index.html` and `countries.json`). The two settings at the top of `index.html`:

* `PLAYLIST_URL` is where the lists come from, with `%s` standing for the country code. It starts as the public iptv-org list on GitHub raw, and the page then does the browser filter itself (no health check, so dead streams cost 12 seconds each like in the app). Once the mirror runs, change it to `https://aerialtv.tv/lists/%s.web.m3u` and the page gets the pre checked list. See "The web list" below.
* `COUNTRIES_URL` is the country list, `countries.json` next to the page. If the file is missing, the copy in this repo on GitHub is used.

Things a browser can't do, so don't expect them: `http://` streams, streams that need a custom user agent or referrer, and anything that isn't HLS. The mirror's web list and the page's own filter both drop those.

## Playlist mirror

A lot of links in the iptv-org lists are dead at any given moment. On 17 October 2026 Pakistan had 34 working streams out of 103, and each dead one costs the viewer up to 12 seconds of black screen while the app skips it. `server/mirror.mjs` does that skipping on a server instead. It downloads each country playlist from GitHub raw, tries every stream the way the app would (same user agent and referrer, 8 second limit, and for HLS it follows the playlist down to a real video segment), then writes a cleaned copy without the dead links.

Streams that answer 401, 403 or 451 are kept, tagged `[Geo-blocked]` and moved to the end, because the server may simply be in the wrong country. Streams iptv-org already marks as geo-blocked are kept too, unless their host doesn't exist or returns 404. The file format doesn't change, so the app reads these lists with its normal parser. Serving them from your own domain also gets round ISPs that block github.io.

Run it locally with Node 18 or newer. There's nothing to install.

```bash
node server/mirror.mjs pk ae
```

That writes `server/out/pk.m3u`, `server/out/ae.m3u` and `server/out/status.json` (counts per country plus the time of the run), and prints a line per country such as `pk: 34 alive, 3 geo-blocked kept, 66 dead (of 103), 35 for the web player`. Leave the codes off to do every country in `countries.json`. Options: `--out <folder>`, `--concurrency 40` (streams checked at once), `--per-host 4` (so one server isn't flooded and wrongly marked dead), `--timeout 8000` (milliseconds per stream) and `-v` to print the verdict for every stream.

A country file is only replaced when the run finds at least one working stream, and it's swapped in whole, so the web server never hands out half a list. If GitHub can't be reached, yesterday's file stays.

### The web list

Next to each `pk.m3u` the job writes `pk.web.m3u`: the same working streams, cut down to the ones a browser can play. That means `https://` only (an HTTPS page can't load `http://` video), HLS only (`.m3u8`, the one live format Safari and hls.js both play) and no entry that asks for a user agent or referrer (`http-user-agent` or `http-referrer` in the `#EXTINF` line or on an `#EXTVLCOPT` line), because a browser can't send those. Same file format, same order, same `[Geo-blocked]` tags at the end. The count goes in `status.json` as `web`. A country whose streams are all `http://` gets an empty web list (just the `#EXTM3U` line), and the page tells the viewer to use the Android app.

The web player fetches these lists from JavaScript on a different origin, so nginx has to allow that. In the `location` block that serves the lists:

```
location /lists/ {
    add_header Access-Control-Allow-Origin * always;
}
```

GitHub raw and jsDelivr already send that header, which is why the page works against iptv-org before the mirror exists.

### Running it every 6 hours

Any small Linux VPS with Node will do. The job waits on the network, not the CPU, so the cheapest plan is enough. Pick a location close to most of your viewers if you can: the checks run from wherever the server sits, and a channel that only plays inside Pakistan will look blocked from Europe.

1. Copy the project to the server, for example to `/opt/aerialtv`. It only needs `server/` and `app/src/main/assets/countries.json`, at those same paths.
2. Install Node (`sudo apt install nodejs` on Ubuntu 24.04 gives Node 18) and a web server such as nginx.
3. Serve a folder like `/var/www/aerialtv/lists` at `https://aerialtv.tv/lists/` over HTTPS (certbot sets up the certificate).
4. Add the job with `crontab -e`:

   ```
   0 */6 * * * cd /opt/aerialtv && /usr/bin/node server/mirror.mjs --out /var/www/aerialtv/lists >> $HOME/aerialtv-mirror.log 2>&1
   ```

Run it once by hand first and read the log. A full run of every country takes much longer than the local test (pk and ae together took about 2 minutes on Faizan's PC, mostly waiting for dead links to time out), which is fine for a job that runs 4 times a day. The app keeps a list for 6 hours, so the two stay roughly in step.

If the website itself is on shared hosting without Node, run the job on the VPS and copy the files over after each run, for example by adding `&& rsync -a /var/www/aerialtv/lists/ user@yourhost:public_html/lists/` to the cron line.

### Pointing the app at it

In `app/build.gradle.kts`, replace the empty value:

```kotlin
buildConfigField("String", "MIRROR_URL", "\"https://aerialtv.tv/lists/%s.m3u\"")
```

The app swaps `%s` for the country code, so Pakistan loads `https://aerialtv.tv/lists/pk.m3u`. Open that address in a browser to check it works, then build and release a new version. The mirror is asked first and gets a 2.5 second head start before the iptv-org hosts are tried. If the mirror is down or missing a country, the app falls back to iptv-org on its own, so nothing breaks.
