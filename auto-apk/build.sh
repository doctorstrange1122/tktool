#!/bin/bash
# ============================================
# 过肥（自动更新）APK 构建脚本
# 此 APK 打开后自动跳转到 GitHub Pages，实现内容自动同步
# 使用 Android SDK build-tools 手动构建，无需 Gradle/Android Studio
# ============================================
set -e

# 配置（根据你的环境修改这些路径）
ANDROID_SDK="${ANDROID_SDK:-/opt/android-sdk}"
BUILD_TOOLS="${BUILD_TOOLS:-$ANDROID_SDK/build-tools/29.0.3}"
ANDROID_JAR="${ANDROID_JAR:-$ANDROID_SDK/platforms/android-29/android.jar}"
ZXING_VERSION="3.5.1"
ZXING_URL="https://maven.aliyun.com/repository/public/com/google/zxing/core/${ZXING_VERSION}/core-${ZXING_VERSION}.jar"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/src/main"
OUT_DIR="$SCRIPT_DIR/build"
KEYSTORE="$OUT_DIR/auto.keystore"
KEYSTORE_PASS="auto123"
KEY_ALIAS="auto"
KEY_PASS="auto123"

echo "========================================"
echo "  过肥（自动更新）APK 构建"
echo "========================================"

# 1. 准备构建目录
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/java_classes" "$OUT_DIR/zxing_classes"

# 2. 下载 ZXing 核心库
echo "[1/7] 下载 ZXing core..."
ZXING_JAR="$OUT_DIR/zxing-core.jar"
if [ ! -f "$ZXING_JAR" ]; then
    curl -sL "$ZXING_URL" -o "$ZXING_JAR"
fi
echo "       ZXing jar: $(ls -la "$ZXING_JAR" | awk '{print $5}') bytes"

# 3. 解压 ZXing class 文件
echo "[2/7] 解压 ZXing classes..."
cd "$OUT_DIR/zxing_classes"
jar xf "$ZXING_JAR"
rm -rf META-INF
cd "$SCRIPT_DIR"
echo "       ZXing classes: $(find "$OUT_DIR/zxing_classes" -name '*.class' | wc -l)"

# 4. 编译 Java 源码
echo "[3/7] 编译 Java 源码..."
javac -source 1.8 -target 1.8 \
    -bootclasspath /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar \
    -cp "$ANDROID_JAR:$ZXING_JAR" \
    -d "$OUT_DIR/java_classes" \
    "$SRC"/java/com/tktool/auto/*.java
echo "       编译完成"

# 5. D8 转换 class → dex
echo "[4/7] D8 转换 dex..."
$BUILD_TOOLS/d8 --lib "$ANDROID_JAR" --output "$OUT_DIR" \
    $(find "$OUT_DIR/java_classes" -name "*.class") \
    $(find "$OUT_DIR/zxing_classes" -name "*.class")
echo "       dex: $(ls -la "$OUT_DIR/classes.dex" | awk '{print $5}') bytes"

# 6. aapt2 链接资源
echo "[5/7] aapt2 链接资源..."
$BUILD_TOOLS/aapt2 link -o "$OUT_DIR/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$SRC/AndroidManifest.xml" \
    -A "$SRC/assets" \
    --auto-add-overlay

# 7. 合并 dex 并签名
echo "[6/7] 打包签名..."
cd "$OUT_DIR"
cp base.apk unsigned.apk
$BUILD_TOOLS/aapt add unsigned.apk classes.dex
$BUILD_TOOLS/zipalign -f -p 4 unsigned.apk aligned.apk

# 创建 keystore（首次）
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KEYSTORE_PASS" -keypass "$KEY_PASS" \
        -dname "CN=Auto Update, OU=Dev, O=TKTool, L=Beijing, ST=Beijing, C=CN"
fi

$BUILD_TOOLS/apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:"$KEYSTORE_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --key-pass pass:"$KEY_PASS" \
    --out tktool_auto.apk \
    aligned.apk

cd "$SCRIPT_DIR"

# 8. 完成
echo "[7/7] 构建完成!"
echo ""
echo "========================================"
echo "  APK: $OUT_DIR/tktool_auto.apk"
echo "  大小: $(ls -la "$OUT_DIR/tktool_auto.apk" | awk '{print $5}') bytes"
echo "========================================"
