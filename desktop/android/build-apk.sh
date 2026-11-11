#!/usr/bin/env bash
# Build the Octop Android dashboard APK without Gradle.
#
# Prerequisites (one-time, created under <repo>/.android-tools/):
#   - JDK 17 (portable zip)
#   - Android SDK cmdline-tools + platforms;android-34 + build-tools;34.0.0
# See desktop/android/README.md for the bootstrap commands.
#
# Usage: desktop/android/build-apk.sh [version-name]   (default: 1.0.3b4)

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
TOOLS="$ROOT/.android-tools"
JAVA="$TOOLS/jdk/bin"
BT="$TOOLS/sdk/build-tools/34.0.0"
PLATFORM="$TOOLS/sdk/platforms/android-34/android.jar"
APP="$HERE/app"
OUT="$APP/build"
VER="${1:-1.0.3b4}"

if [ ! -f "$PLATFORM" ]; then
    echo "ERROR: Android SDK not found under $TOOLS — see desktop/android/README.md" >&2
    exit 1
fi

export JAVA_HOME="$(cygpath -w "$TOOLS/jdk")"
export PATH="$JAVA:$PATH"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"

# 1) Compile + link resources, generate R.java and the base APK
"$BT/aapt2.exe" compile --dir "$APP/res" -o "$OUT/res.zip"
"$BT/aapt2.exe" link -o "$OUT/base.apk" -I "$PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    --min-sdk-version 24 --target-sdk-version 34 \
    --version-code 1 --version-name "$VER" \
    --java "$OUT/gen" \
    "$OUT/res.zip"

# 2) Compile Java sources (app + generated R.java) — javac needs Windows paths in @file
find "$APP/java" "$OUT/gen" -name "*.java" | while read -r f; do cygpath -w "$f"; done > "$OUT/sources.txt"
"$JAVA/javac.exe" -source 1.8 -target 1.8 -nowarn \
    -bootclasspath "$PLATFORM" -d "$OUT/classes" @"$OUT/sources.txt"

# 3) Dex (jar the classes first; d8/apksigner run via java -cp to avoid .bat path quirks)
"$JAVA/jar.exe" cf "$OUT/classes.jar" -C "$OUT/classes" .
"$JAVA/java.exe" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --release --lib "$PLATFORM" --min-api 24 \
    --output "$OUT" "$OUT/classes.jar"

# 4) Pack classes.dex into the APK (zip tool is not available in git-bash)
python - "$OUT/base.apk" "$OUT/classes.dex" <<'PYEOF'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
print("packed classes.dex ->", apk)
PYEOF

# 5) Align
"$BT/zipalign.exe" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

# 6) Debug keystore (created once, reused across builds)
KS="$TOOLS/debug.keystore"
if [ ! -f "$KS" ]; then
    "$JAVA/keytool.exe" -genkeypair -keystore "$KS" \
        -storepass android -keypass android -alias androiddebugkey \
        -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000
fi

# 7) Sign
APK="$OUT/Octop-android-$VER.apk"
"$JAVA/java.exe" -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool \
    sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$APK" "$OUT/aligned.apk"

# 8) Verify
"$JAVA/java.exe" -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool \
    verify --print-certs "$APK"
"$BT/aapt2.exe" dump badging "$APK" | head -2
echo
echo "OK: $APK"
