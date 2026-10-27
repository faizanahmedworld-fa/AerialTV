# Publishing Aerial TV on Google Play

This is the full checklist for getting Aerial TV 1.3 onto the Play Store. The parts only you can do are marked **you**. Everything else is already prepared in the repo.

## 1. Open the developer account (you)

Google only lets the account holder do this, and it needs a card and an identity check.

1. Sign in to Google with the account you want to own the app, then go to https://play.google.com/console/signup.
2. Pick **Personal** account (an **Organisation** account needs a registered business and a D-U-N-S number, but skips the tester rule below).
3. Pay the one time 25 USD registration fee.
4. Verify your identity. Google asks for a government ID and sometimes a proof of address. This usually clears in 1 to 3 days.
5. Verify the developer email and phone number.

Personal accounts created after November 2023 have one extra rule: before the app can go to **Production**, it must run a **closed test with at least 12 testers for 14 days in a row**. Plan for this. Ask friends, family or your WhatsApp contacts to opt in. Details in section 6.

## 2. Create the app (you, 5 minutes)

In Play Console press **Create app**:

| Field | Value |
| --- | --- |
| App name | Aerial TV |
| Default language | English (United Kingdom) |
| App or game | App |
| Free or paid | Free |
| Declarations | Tick the Developer Programme Policies and US export laws boxes |

## 3. Store listing

Copy and paste from here. Everything fits Play's limits.

**App name** (30 max)

```
Aerial TV: Free Live TV
```

**Short description** (80 max)

```
Free live TV from 189 countries. Pick your country once and it plays. No sign up.
```

**Full description** (4000 max)

```
Aerial TV turns your Android TV, Google TV, Fire TV Stick, phone or tablet into a free TV set. Pick your country once and live channels start playing. No account to make, no subscription, no ads.

WHAT YOU GET
• Live channels from 189 countries, built on the open iptv-org playlists
• Your country is picked for you on first run, change it any time
• A short setup asks who is watching (kids, family or adults), what you like and which languages you speak. That builds a For you row, and Most watched fills itself from what you leave on
• Signal bars next to every channel show which streams are answering right now
• Search channels by name, number or type ("sport", "news")
• Menus in English, Chinese, Arabic, Spanish, French, German, Portuguese, Russian, Hindi, Urdu, Turkish and Indonesian

MADE FOR A REMOTE
• Up and down change channel, number keys jump straight to a channel
• OK opens the channel list, hold OK to add a favourite
• Dead streams are skipped on their own, and channels with backup links try each one first
• Opens on the last channel you watched, and remembers your favourites across updates

ALSO ON PHONES AND TABLETS
Tap for the list, swipe up or down to change channel.

PRIVACY
Nothing you do is sent to us. There is no account, no analytics and no tracking. Favourites and history stay on your device.

ABOUT THE CHANNELS
Aerial TV is a player. It plays free public streams that broadcasters already publish openly and that the iptv-org project lists. We do not host, own or sell any video. Which channels work depends on the broadcaster and on where you are: some streams are only offered inside their home country.

Aerial TV is a small, open source app of about 2 MB.
```

**App category:** Video Players & Editors
**Tags:** Live TV, Streaming, Video players
**Contact email:** your developer email (shown publicly)
**Website:** https://faizanahmedworld-fa.github.io/AerialTV/
**Privacy policy URL:** https://faizanahmedworld-fa.github.io/AerialTV/privacy/ (the page is in `web/privacy/index.html`, pushed with this release)

## 4. Graphics

Play needs these files. Sizes are exact.

| Asset | Size | Where it is |
| --- | --- | --- |
| App icon | 512 x 512 PNG, no transparency | `docs/playstore/Icon 512.png` |
| Feature graphic | 1024 x 500 PNG | `docs/playstore/Feature graphic.png` |
| Phone screenshots (2 to 8) | 16:9 or 9:16, at least 320 px | `docs/playstore/Screenshot *.png` (1920 x 1080 from the TV emulator work for phones too) |
| TV screenshots (at least 1) | 1920 x 1080 | same files |
| TV banner | 1280 x 720 PNG | `docs/playstore/TV banner.png` |

Run `tools/playstore.ps1` to redraw the icon, feature graphic and TV banner from the same shapes the launcher icon uses.

## 5. The forms Play makes you fill in (you, about 20 minutes)

Go through **App content** in the left menu. Answers for Aerial TV:

**Privacy policy:** the URL above.

**Ads:** No, the app does not contain ads. (When you add ads later, change this to Yes, add the `com.google.android.gms.permission.AD_ID` permission, and update the Data safety form to say Advertising ID is collected.)

**App access:** All functionality is available without special access.

**Content rating:** Start the IARC questionnaire, category **Utility, Productivity, Communication or Other**. Answer No to violence, sexuality, language, controlled substances, gambling. Then: does the app let users share content? No. Does it share location? No. Does it allow purchases? No. Does it contain user generated content? No. You will get Everyone / PEGI 3.

Note the honest caveat: Aerial TV plays whatever the broadcasters publish, including news channels. Google accepts that for a player app, the same way it does for browsers.

**Target audience:** 18 and over. Do not tick the under 13 boxes, or the app falls under the Families policy and needs extra review.

**News app:** No.

**COVID tracing:** No.

**Data safety:** Does your app collect or share user data? **No.** The app has no accounts, analytics or tracking. Data stays on the device, and the stream and playlist requests are standard network requests Google does not count as collection. Encryption and deletion questions do not appear when you answer No.

**Government app:** No.

**Financial features:** None.

**Health:** None.

## 6. Testing track and the 12 tester rule

1. **Release, Testing, Closed testing.** Create a track called "Friends".
2. Upload `app/build/outputs/bundle/release/app-release.aab` (built on 27 October 2026, version 1.3, versionCode 4). Play will offer **Play App Signing**. Accept it. Your existing key in `signing/aerialtv-release.jks` becomes the upload key, so the same `./gradlew bundleRelease` command keeps working.
3. Add testers by email list, at least 12 Gmail addresses, then share the opt in link Play gives you.
4. Each tester opens the link, presses "Become a tester", then installs from Play. They must stay opted in for 14 days.
5. After 14 days, press **Apply for production access** on the dashboard. Google asks a few questions about what you learnt from testing. Short honest answers are fine ("Tested on Fire TV Stick 4K, Mi Box and three phones; fixed the channel list focus on older boxes").
6. Approval takes up to 7 days. Then create a Production release with the same AAB.

If you later register as an organisation instead, the 12 tester step disappears.

## 7. Android TV

Because the app declares the Leanback launcher, Play reviews it for TV separately. It already meets the TV rules: a 320 x 180 banner, D-pad only navigation, no touch requirement, landscape only, and no runtime permissions. In **Release, Advanced settings, Form factors**, tick **Android TV** so the app shows up on TV devices. TV review can take an extra week.

## 8. Things that could get the app rejected, and what to say

Play is strict with IPTV players because most of them stream pirated pay TV. Aerial TV is different and the listing says so, but be ready for a question from the review team. Keep these facts handy:

* The playlists come from the iptv-org project on GitHub, which lists only streams broadcasters publish openly and removes anything a rights holder asks it to.
* The app hosts nothing and sells nothing. There is no subscription, no M3U login, no "add your own playlist" box.
* It contains no ads today.

If a specific channel gets flagged, the fix is on the iptv-org side (open an issue there) and the next app start picks up the new list.

## 9. Ads later

Nothing has to change in the app now. When you want ads:

1. Open an AdMob account, link it to the Play app.
2. Add the Google Mobile Ads SDK and the `AD_ID` permission.
3. Best spot for a TV app is a small banner on the channel list screen, not over the video. Interstitials between channel changes get the app down rated fast.
4. Update the Ads and Data safety answers in Play Console before uploading the ad build.

## 10. Every later release

```bash
export JAVA_HOME="/c/Users/faiza/AndroidBuild/jdk/jdk-17.0.20.1+1"
./gradlew bundleRelease
```

Bump `versionCode` and `versionName` in `app/build.gradle.kts` first. Upload `app/build/outputs/bundle/release/app-release.aab` to the track, write the release notes, roll out. The GitHub APK release stays as it is for sideloading.
