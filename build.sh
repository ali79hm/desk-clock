#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to an Android SDK}"
: "${ECJ_JAR:?Set ECJ_JAR to the Eclipse Java compiler JAR}"
: "${SIGNING_KEYSTORE:?Set SIGNING_KEYSTORE to your APK signing keystore}"
: "${SIGNING_KEY_ALIAS:?Set SIGNING_KEY_ALIAS to your key alias}"
: "${SIGNING_PASSWORD_FILE:?Set SIGNING_PASSWORD_FILE to a file containing your keystore password}"

project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
min_sdk=16
apk_name=JClock-Plus.apk
android_jar="$ANDROID_SDK_ROOT/platforms/android-$min_sdk/android.jar"
tools="${ANDROID_BUILD_TOOLS:-$ANDROID_SDK_ROOT/build-tools/34.0.0}"
build="$project/build"
mkdir -p "$build"
work="$(mktemp -d "$build/work.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/classes" "$work/dex"
mapfile -d '' sources < <(find "$project/src" -type f -name '*.java' -print0)

"$tools/aapt" package -f -M "$project/AndroidManifest.xml" -S "$project/res" -A "$project/assets" \
    -I "$android_jar" -F "$work/unsigned.apk"
java -jar "$ECJ_JAR" -encoding UTF-8 -source 1.7 -target 1.7 \
    -bootclasspath "$android_jar" -d "$work/classes" "${sources[@]}"
mapfile -d '' classes < <(find "$work/classes" -type f -name '*.class' -print0)
"$tools/d8" --min-api "$min_sdk" --lib "$android_jar" --output "$work/dex" "${classes[@]}"
zip -q -j "$work/unsigned.apk" "$work/dex/classes.dex"
"$tools/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"
"$tools/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_KEY_ALIAS" \
    --ks-pass "file:$SIGNING_PASSWORD_FILE" --v1-signing-enabled true --v2-signing-enabled false \
    --v3-signing-enabled false --v4-signing-enabled false \
    --out "$work/$apk_name" "$work/aligned.apk"
"$tools/apksigner" verify --min-sdk-version "$min_sdk" "$work/$apk_name"
mv -- "$work/$apk_name" "$build/$apk_name"
