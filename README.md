# InstaScroll

Scroll Instagram with your volume buttons.

**[Download](https://github.com/AUstnnnnnn/InstaScroll/releases/latest/download/InstaScroll.apk)** · **[Website](https://austnnnnnn.github.io/InstaScroll/)**

## Controls

| Button | Action |
|--------|--------|
| Volume Up | Scroll to previous post |
| Volume Down | Scroll to next post |
| 2x Volume Down | Like the post |

Only active when Instagram is in the foreground. Volume keys work normally in every other app.

## Setup

1. Install the APK
2. Open InstaScroll → **Open Accessibility Settings**
3. Enable **InstaScroll**
4. Open Instagram and use volume keys

## Build

Requires JDK 17+ and Android SDK (build-tools 36, platform android-36).

```sh
./build.sh
adb install build/InstaScroll.apk
```
