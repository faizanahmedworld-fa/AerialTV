# Aerial TV for Windows

The same TV as the Android app, for Windows 10 and 11 PCs and laptops. Pick your country once and it opens straight onto a channel. Dead streams are skipped on their own, each channel tries its backup links, and the last channel you watched comes back next time.

## Installing it (for viewers)

1. Download `Aerial TV Setup 1.0.0.exe` from the [Releases page](https://github.com/faizanahmedworld-fa/AerialTV/releases). GitHub may show the name with dots instead of spaces.
2. Double click it. There are no questions to answer: it installs in a few seconds and opens.
3. Windows may say "Windows protected your PC" because the installer isn't signed with a paid certificate yet. Click **More info**, then **Run anyway**.

It puts Aerial TV on the desktop and in the Start menu. To remove it, use Settings, Apps, Installed apps, Aerial TV, Uninstall.

### Controls

| Key or mouse | What it does |
| --- | --- |
| ▲ ▼ or Page Up / Page Down | Change channel |
| Enter, ◀ or a single click | Open the channel list |
| ▶ or I | Show what's on |
| Number keys | Jump to a channel |
| Backspace | Go back to the last channel |
| F, F11 or double click | Full screen |
| Esc | Close the list, or leave full screen |
| M, or the mouse wheel | Mute, or volume |
| Right click a channel, or F in the list | Add to Favourites |

Moving the mouse shows a small bar with the same buttons.

### Updates

About 8 seconds after it opens, the app checks GitHub for a newer Windows release. If there is one, a box offers **Update now**. That downloads the new installer, checks it isn't damaged, installs it quietly and reopens Aerial TV. There's also **Check for updates** at the bottom of the channel list.

The check reads the public GitHub API, so **the repository has to be public** for updates and downloads to work. While it's private, the app just carries on without updating.

## Building it

You need Node 18 or newer (Node 24 is on Faizan's PC). From this folder:

```bash
npm install
```

Run it without installing:

```bash
npm start
```

Make the installer, which lands in `dist/Aerial TV Setup <version>.exe` (about 100 MB, nearly all of it the Electron runtime):

```bash
npm run dist
```

The country list comes from `../app/src/main/assets/countries.json`, so both apps always offer the same countries. The icon is `build/icon.ico`, drawn by the same script as the Android icons:

```bash
powershell -ExecutionPolicy Bypass -File ../tools/icons.ps1 -Windows build
```

## Releasing a new version

GitHub builds and publishes it for you.

1. Raise `version` in `package.json`, for example to `1.0.1`.
2. Commit, then tag and push:

   ```bash
   git tag windows-v1.0.1
   ```

   ```bash
   git push origin main windows-v1.0.1
   ```

3. The "Windows release" job in the Actions tab builds the installer and creates a release called "Aerial TV for Windows windows-v1.0.1" with the `.exe` attached. Within a day, everyone on an older version gets the update box.
4. Update the download link in the main [README](../README.md) (the "Windows PC or laptop" section) to the new file name, `Aerial.TV.Setup.1.0.1.exe`.

The tag must start with `windows-v` and match `package.json`, or the job stops. Android releases keep their own `v1.1` style tags, and the Windows app ignores them.

## How it works

| File | What's in it |
| --- | --- |
| `main.js` | Window, playlist download (mirror, iptv-org, GitHub raw and jsDelivr, 2.5 seconds apart, 6 hour cache), stream headers, updater |
| `preload.js` | The small bridge the page uses to talk to `main.js` |
| `renderer/app.js` | The TV screen: zapping, auto skip, channel list, country picker, update box |
| `renderer/m3u.js` | Playlist reader, a straight port of `M3uParser.kt` |

Streams play through hls.js (HLS), mpegts.js (raw MPEG-TS and FLV) or the built in player, in the same try this, then that order as the Android app, 12 seconds per try. `MIRROR_URL` at the top of `main.js` works the same as in the Android build.

Country flags use the Twemoji flag font (graphics by Twitter, CC BY 4.0), because Windows doesn't draw flag emojis.
