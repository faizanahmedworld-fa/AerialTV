# Aerial TV on Apple devices

Short version: the app can be built for iPhone, iPad and Apple TV, but it can't be handed out the way the Android APK is. Apple only lets people install from the App Store, and the App Store's content rules are the problem, not the code. The one route that works today with no permission from Apple is a web version of Aerial TV that plays in Safari and sends the picture to an Apple TV with AirPlay. Details and numbers below, checked on 17 October 2026.

## How apps get onto Apple devices

**iPhone and iPad.** The App Store is the only route for people in the UAE, Pakistan and most of the world. Installing from outside the store (alternative app marketplaces and Apple's "web distribution") exists only where a law forced it: the EU since 2024, Japan since December 2025, and Brazil according to a June 2026 report. None of that reaches our viewers.

Sideloading with a free Apple ID (AltStore, Sideloadly) needs a computer, the app stops working after 7 days unless it's re signed, and the ID can hold 3 such apps. Fine for you to test with. Not something to ask a relative in Lahore to do.

**TestFlight** is Apple's beta channel and the closest thing to sideloading. It needs the Apple Developer Program ($99 a year), gives you a public link, and takes up to 10,000 testers. But each build dies after 90 days, the first build of every version goes through Beta App Review, and Apple can pull the link. It's a way to test with family, not a way to ship.

**Apple TV** has no browser and no USB file route, so the Downloader trick from the [install steps](../README.md#install-it) has no equivalent. It's the App Store or TestFlight (the viewer installs the TestFlight app on the Apple TV and redeems a code). Developer builds can be pushed over a cable with Xcode or Apple Configurator, limited to 100 registered devices a year, which is for your own testing only.

**Mac.** A Mac app can be downloaded from your website like the APK. Apple's notarisation is an automated malware scan, not a content review. Nobody is asking for Aerial TV on a Mac though, and the web version below covers it anyway.

## Why the App Store is the real blocker

Apple's App Review Guidelines, section 5.2.3:

> Apps should not facilitate illegal file sharing or include the ability to save, convert, or download media from third-party sources (such as Apple Music, YouTube, SoundCloud, Vimeo, etc.) without explicit authorization from those sources. Streaming of audio/video content may also violate Terms of Use, so be sure to check before your app accesses those services. Authorization must be provided upon request.

And 5.2.1 says apps must be submitted by whoever "owns or has licensed the intellectual property". The iptv-org CC0 licence covers the playlist file, not the video. A fair share of the streams in those lists are broadcaster channels relayed by someone else, and if a reviewer asks for authorisation there's nothing to send.

That's why the IPTV players that survive on the App Store all ship empty and make the user paste their own M3U link. Their listings say so in as many words: "does not contain any preloaded playlists or channels". An App Store Aerial TV would have to open on a blank screen and ask for a playlist URL, which is the opposite of what the app is for. Apple also removes apps after rights holder complaints, so even a build that gets through can vanish later.

The Android app exists because sideloading needs nobody's approval. Nothing on Apple's side works like that outside the EU and Japan.

## What a native build would involve, if you ever go for it

The Android code can't be reused as is. Kotlin on Android and Swift on iOS and tvOS are different worlds, and the whole app is 7 Kotlin files. The parser, playlist download and country detection (about 300 lines) port in a day. `MainActivity.kt` (595 lines of Android UI, remote handling and the auto skip logic) is a rewrite. For an app this small a straight Swift rewrite is simpler than sharing code through Kotlin Multiplatform or moving to Flutter.

Playback is the easy part. Apple's own player (AVPlayer) plays HLS natively, and the lists are nearly all HLS:

| Playlist | Streams | HLS (`.m3u8`) | Plain `http://` | Need a user agent | Need a referrer |
| --- | --- | --- | --- | --- | --- |
| Pakistan (iptv-org) | 103 | 94 | 49 | 0 | 2 |
| Ukraine (iptv-org) | 200 | 196 | 130 | 164 | 24 |
| UAE (mirror output) | 19 | 19 | 0 | 0 | 0 |

The gaps:

* Streams that aren't HLS need a third party player such as VLCKit, which is allowed on the App Store and adds about 30 MB.
* Plain `http://` streams are blocked by iOS by default. The app would set `NSAllowsArbitraryLoadsForMedia` in its Info.plist, which Apple permits for video.
* Per stream user agent and referrer go through `AVURLAssetHTTPHeaderFieldsKey`. It isn't in Apple's documentation but every IPTV app on the store uses it.
* The Siri Remote has no number keys and no channel up or down buttons. Up and down become swipes on the touch pad, click opens the list, and the tvOS focus engine replaces most of the key handling.
* Apple TV wants its own artwork: a layered app icon and a top shelf image, so `tools/icons.ps1` would need a tvOS version.

Costs: the Apple Developer Program at $99 a year, a Mac with Xcode (a rented cloud Mac or a GitHub Actions macOS runner works for the build, but you still want a real iPhone and Apple TV to test on), and roughly 2 to 4 weeks for iPhone plus Apple TV. Then the review gamble above.

## What works now: a web version

Safari on iPhone and iPad plays HLS inside a normal `<video>` tag with no library, which no other phone browser does. So a page on your own domain can load the same country list, show the same channel guide and play the stream. "Add to Home Screen" gives it an icon and a full screen window, so it feels like an app. And the AirPlay button in the video controls sends the picture to any Apple TV on the same WiFi. The phone becomes the remote. That's the Apple TV route with no App Store, no $99 and no review.

It has limits, and the numbers above show how much they bite:

* An HTTPS page can't play `http://` streams. Pakistan would lose 49 of 103 streams, Ukraine 130 of 200. The fix would be relaying those through your server, which turns the server into the thing streaming the video. Don't. Just show the HTTPS ones.
* A browser can't send a custom user agent or referrer. Pakistan hardly needs them (2 streams). Ukraine needs a user agent for 164 of 200, so some countries would be thin.
* Anything that isn't HLS won't play (9 of 103 in Pakistan).
* iOS won't start video without a tap, so the first channel needs a press on the screen. After that, swipes work.
* The page needs to fetch the playlist text, so the server that serves the lists has to send an `Access-Control-Allow-Origin: *` header. GitHub raw and jsDelivr already do. nginx on the mirror needs one line.

The mirror job in `server/mirror.mjs` already decides per stream whether it's alive and what headers it wants, so it writes a second file per country with only the streams a browser can play, for example `pk.web.m3u`. The page stays dumb and the filtering happens once every 6 hours like everything else.

The same page works in Chrome on Android (with the hls.js library, since Chrome doesn't play HLS on its own), on Windows and on a Mac. It's the first thing a visitor to aerialtv.tv could use without installing anything.

**Built on 17 October 2026 and live at [faizanahmedworld-fa.github.io/AerialTV](https://faizanahmedworld-fa.github.io/AerialTV/).** GitHub Pages publishes the `web` folder on every push to `main`, so there's no server to run until aerialtv.tv exists. The page is `web/index.html`, one file with no build step, plus a copy of the country list next to it. It plays Pakistan's HTTPS HLS streams in Chrome through hls.js and in Safari through the video tag, remembers the country and last channel, changes channel with swipes and arrow keys, skips a dead stream after 12 seconds like the Android app, and shows the AirPlay button when an Apple TV is on the network. Until the mirror is live it reads the raw iptv-org list and applies the browser filter itself (43 of Pakistan's 103 streams on the day it was built). How to put it on the domain is in [BUILDING.md](../BUILDING.md#web-player).

## Suggested order

1. Done: the web player is live on GitHub Pages. Next is moving it to the domain and feeding it the mirror's browser safe lists, because some internet providers block github.io.
2. Leave native iOS and tvOS until the Android app and the domain are established and people are actually asking. If that day comes, budget for a rejection and have a "paste your own playlist" mode ready as the fallback that gets the app approved.
3. Skip a Mac app.

## Sources

* [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/), sections 5.2.1 and 5.2.3
* [TestFlight](https://developer.apple.com/testflight/): 10,000 external testers, 90 day builds
* [Apple Developer Program](https://developer.apple.com/programs/): $99 a year
* [iOS 26.2 beta: alternative app stores in Japan](https://gigazine.net/gsc_news/en/20251106-ios-26-2-beta-1-alt-app-store)
* [Ad hoc distribution of tvOS IPA, Apple Developer Forums](https://developer.apple.com/forums/thread/25525): no browser on Apple TV to open an install link
* [IPTVX FAQ](https://iptvx.app/faq/index.html), an example of an App Store IPTV player that ships with no channels
