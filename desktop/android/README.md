# Octop Android (dashboard WebView client)

Minimal Android client for the Octop dashboard: a WebView shell that asks for
the Octop server address on first launch (persisted), then loads the dashboard.
Supports file upload (attachments) and in-app back navigation.

The Wails desktop shell (`desktop/src/`) cannot run on Android — it spawns a
local Octop Python process. This app instead connects to an already-running
Octop server (LAN or remote), same as the dashboard's PWA install, but
packaged as an APK.

## Layout

| Path | Role |
|------|------|
| `app/AndroidManifest.xml` | Package `com.octop.dashboard`, minSdk 24, targetSdk 34 |
| `app/java/.../MainActivity.java` | WebView + first-run server picker |
| `app/res/` | Icon (from `desktop/src/build/appicon.png`) + app name |
| `build-apk.sh` | No-Gradle build: aapt2 → javac → d8 → zipalign → apksigner |

## One-time toolchain bootstrap (~400 MB, stored in `<repo>/.android-tools/`)

```bash
mkdir -p .android-tools/dl && cd .android-tools/dl
curl -LO https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip
curl -LO https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip
cd ..
unzip -q dl/OpenJDK17U-jdk_x64_windows_hotspot_*.zip && mv jdk-17* jdk
mkdir -p sdk/cmdline-tools && unzip -q dl/commandlinetools-win-*.zip -d sdk/cmdline-tools
mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest
export JAVA_HOME="$(cygpath -w "$PWD/jdk")"
yes | ./sdk/cmdline-tools/latest/bin/sdkmanager.bat --sdk_root="$(cygpath -w "$PWD/sdk")" --licenses
./sdk/cmdline-tools/latest/bin/sdkmanager.bat --sdk_root="$(cygpath -w "$PWD/sdk")" \
    "platforms;android-34" "build-tools;34.0.0"
```

## Build

```bash
desktop/android/build-apk.sh [version]   # default 1.0.3b4
# -> desktop/android/app/build/Octop-android-<version>.apk (debug-signed)
```

## Install

```bash
adb install -r desktop/android/app/build/Octop-android-*.apk
```

Or copy the APK to a phone and open it (enable "install unknown apps").

## Notes & limitations

- Debug-signed (`.android-tools/debug.keystore`, password `android`). For a
  Play-Store or shared release build, sign with a real key via `apksigner`.
- `usesCleartextTraffic="true"` allows plain `http://` LAN servers.
- To change the server address later: clear the app's data (the URL is stored
  in SharedPreferences `octop/server_url`).
