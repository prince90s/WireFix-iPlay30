#!/bin/bash
# ============================================================
#  从源码重新编译 WireFix.apk
#  适用平台：Windows(Git Bash) / macOS / Linux
#  依赖：JDK 17、Android SDK build-tools(含 aapt2/d8/zipalign/apksigner)
#        platform/android-11/android.jar（或任意 SDK 30+ 的 android.jar）
#
#  用法：./build.sh /path/to/jdk17 /path/to/build-tools /path/to/android.jar
#  例：  ./build.sh ../../tools/jdk-17.0.13+11 ../../tools/android-14 ../../tools/android-11/android.jar
# ============================================================
set -e

JDK=${1:?"用法: ./build.sh <jdk目录> <build-tools目录> <android.jar>"}
BT=${2:?"缺少 build-tools 目录"}
AJAR=${3:?"缺少 android.jar 路径"}

KIT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$KIT/build"
mkdir -p "$OUT/classes" "$OUT/dex"

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

echo "==> 1/6 编译 Java"
"$JDK/bin/javac" -source 8 -target 8 -encoding UTF-8 \
  -cp "$AJAR" -d "$OUT/classes" \
  "$KIT/src/AudioWire.java" "$KIT/src/GuardService.java" \
  "$KIT/src/MainActivity.java" "$KIT/src/StateReceiver.java"

echo "==> 2/6 转换 dex"
CLASSES=$(find "$OUT/classes" -name "*.class" | tr '\n' ' ')
"$BT/d8.bat" --release --min-api 29 --lib "$AJAR" --output "$OUT/dex" $CLASSES 2>/dev/null \
  || "$BT/d8" --release --min-api 29 --lib "$AJAR" --output "$OUT/dex" $CLASSES

echo "==> 3/6 打包资源与 manifest"
rm -f "$OUT/WireFix-unsigned.apk"
"$BT/aapt2" link -o "$OUT/WireFix-unsigned.apk" -I "$AJAR" \
  --manifest "$KIT/apk/AndroidManifest.xml" \
  --min-sdk-version 29 --target-sdk-version 30 \
  --version-code 1 --version-name 1.0 2>/dev/null \
  || "aapt2" link -o "$OUT/WireFix-unsigned.apk" -I "$AJAR" \
  --manifest "$KIT/apk/AndroidManifest.xml" \
  --min-sdk-version 29 --target-sdk-version 30 \
  --version-code 1 --version-name 1.0

echo "==> 4/6 塞入 classes.dex"
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"
( cd "$OUT" && jar uf WireFix-unsigned.apk classes.dex )

echo "==> 5/6 对齐"
"$BT/zipalign" -f 4 "$OUT/WireFix-unsigned.apk" "$OUT/WireFix-aligned.apk" 2>/dev/null \
  || "$BT/zipalign.exe" -f 4 "$OUT/WireFix-unsigned.apk" "$OUT/WireFix-aligned.apk"

echo "==> 6/6 签名"
"$BT/apksigner.bat" sign --ks "$KIT/apk/debug.keystore" \
  --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --min-sdk-version 29 \
  --out "$KIT/apk/WireFix.apk" "$OUT/WireFix-aligned.apk" 2>/dev/null \
  || "$BT/apksigner" sign --ks "$KIT/apk/debug.keystore" \
  --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --min-sdk-version 29 \
  --out "$KIT/apk/WireFix.apk" "$OUT/WireFix-aligned.apk"

echo ""
echo "构建完成: $KIT/apk/WireFix.apk"
