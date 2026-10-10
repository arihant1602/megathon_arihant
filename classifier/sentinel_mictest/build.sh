#!/usr/bin/env bash
# Builds the mic test APK with plain SDK build-tools (no Gradle/Kotlin needed).
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-37.0/android.jar
rm -rf build && mkdir -p build/classes build/dex

"${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk}/bin/javac" --release 17 -nowarn -classpath "$JAR" -d build/classes $(find src -name '*.java')
"$BT/d8" --min-api 29 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
"$BT/aapt2" link --manifest AndroidManifest.xml -I "$JAR" --debug-mode -o build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

KS=debug.keystore
[ -f "$KS" ] || "${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk}/bin/keytool" -genkeypair -keystore "$KS" -storepass android -keypass android -alias debug \
  -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Sentinel Mic Test" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --out build/sentinel-mictest.apk build/aligned.apk
echo "built $(pwd)/build/sentinel-mictest.apk"
