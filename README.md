# Beetle Home

A reading-first Android launcher for Boox e-readers, built for the Note Air4 C.

The home screen leads with the book you have open in Kindle (cover, title, author, progress and a big **Continue reading** button), then your widgets, a row of pinned apps, and **All apps**.

Designed for e-ink: white background, black type, no animations, and pages instead of scrolling. Covers and app icons stay in colour, with a saturation boost for the Kaleido screen that you can turn off.

## Install

Download the latest APK on the Boox itself:

**https://github.com/ceruleanscarab/boox-launcher/releases/latest/download/beetle-home.apk**

Every push to `main` builds a new release through GitHub Actions. Updates install over the old version. (While the repo is private that link needs a GitHub sign-in.)

## Setup on the device

1. Open **Beetle Home** from the Boox app list and tap **Settings**.
2. Tap **Choose home app** and pick Beetle Home, then press Home.
3. Tap **Open accessibility settings** and turn on **Beetle Home**. This is what reads the book title from the Kindle app.
4. In the Boox **App Management / Freeze** settings, make sure Beetle Home isn't frozen or restricted in the background, or Boox may shut the accessibility service off.
5. Open a book in Kindle and tap the middle of the page once so the title bar shows. Go Home and the cover appears.

## Using it

- **Book tile**: tap anywhere on it to jump back into Kindle.
- **All apps**: paged grid with Previous / Next. Press and hold an app to pin it to home, hide it, or open its app info. Home or Back closes the list.
- **Pinned apps**: up to six. Press and hold one to move or unpin it, or reorder them in Settings.
- **Widgets**: Settings → **Add a widget**. Each widget spans the screen width; use Shorter / Taller / Up / Remove in Settings to arrange them. The first time, Android asks whether Beetle Home may host widgets.
- **Hidden apps** come back from Settings.

## How book detection works

Same approach as [boox-kindle-cover](https://github.com/ceruleanscarab/boox-kindle-cover), and the two run side by side:

1. An accessibility service watches only `com.amazon.kindle` and reads the title (and author, when shown) off the reader's title bar.
2. The cover is looked up on Apple Books, then Google Books, then Open Library, and saved inside the app.
3. If the Kindle footer is showing a percentage, that becomes the progress bar. This part is a best guess; if it shows something odd, switch Kindle's footer to another reading-progress style and the bar disappears.

If a book isn't picked up, type its title into **Set the book by hand** in Settings. If your Kindle version uses a different view id for the title, put the regex you found with boox-kindle-cover's debug dump into **Title view-id regex**.

The only data leaving the device is the title and author sent to the cover lookup services.

## If Boox won't cooperate

Some Boox firmware hides the stock Android choosers. With USB debugging on:

```
# make Beetle Home the home app
adb shell cmd package set-home-activity net.bluebeetle.launcher/.HomeActivity

# let it host widgets without the permission prompt
adb shell appwidget grantbind --package net.bluebeetle.launcher --user 0
```

To go back to the Boox launcher, choose it again as the home app, or uninstall Beetle Home.

## Notes

- No third-party libraries: plain Android framework views, so the APK is tiny and builds fast.
- The signing key in `keystore/` is a throwaway debug key committed on purpose so CI builds can update each other. Don't reuse it for anything else.
