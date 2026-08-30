#!/bin/bash
# ============================================
# 过肥（悬浮窗版）APK 构建脚本
# 包名: com.tktool.test，独立签名，与正式版共存
# ============================================
set -e

ANDROID_SDK="${ANDROID_SDK:-/opt/android-sdk}"
BUILD_TOOLS="${BUILD_TOOLS:-$ANDROID_SDK/build-tools/29.0.3}"
ANDROID_JAR="${ANDROID_JAR:-$ANDROID_SDK/platforms/android-29/android.jar}"
ZXING_VERSION="3.5.1"
ZXING_URL="https://maven.aliyun.com/repository/public/com/google/zxing/core/${ZXING_VERSION}/core-${ZXING_VERSION}.jar"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/src/main"
OUT_DIR="$SCRIPT_DIR/build"
KEYSTORE="$OUT_DIR/test_v2.keystore"
KEYSTORE_PASS="tktool2026"
KEY_ALIAS="tktool_v2"
KEY_PASS="tktool2026"

echo "========================================"
echo "  过肥工具 APK 构建"
echo "  包名: com.tktool.guofei"
echo "========================================"

# 1. 准备构建目录
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/java_classes" "$OUT_DIR/zxing_classes"

# 2. 编译资源文件
echo "[1/8] 编译资源文件..."
$BUILD_TOOLS/aapt2 compile -o "$OUT_DIR/compiled_res.zip" \
    --dir "$SRC/res" 2>&1
mkdir -p "$OUT_DIR/compiled_res"
cd "$OUT_DIR/compiled_res" && unzip -o ../compiled_res.zip > /dev/null && cd "$SCRIPT_DIR"
echo "       资源文件: $(ls "$OUT_DIR/compiled_res"/*.flat 2>/dev/null | wc -l) 个"

# 3. 下载 ZXing
echo "[2/8] 下载 ZXing core..."
ZXING_JAR="$OUT_DIR/zxing-core.jar"
if [ ! -f "$ZXING_JAR" ]; then
    curl -sL "$ZXING_URL" -o "$ZXING_JAR"
fi
echo "       ZXing jar: $(ls -la "$ZXING_JAR" | awk '{print $5}') bytes"

# 4. 解压 ZXing
echo "[3/8] 解压 ZXing classes..."
cd "$OUT_DIR/zxing_classes"
unzip -q "$ZXING_JAR"
rm -rf META-INF
cd "$SCRIPT_DIR"
echo "       ZXing classes: $(find "$OUT_DIR/zxing_classes" -name '*.class' | wc -l)"

# 5. aapt2 链接资源（先生成 R.java 供 javac 使用）
echo "[4/8] aapt2 链接资源..."
FLAT_FILES=$(ls "$OUT_DIR/compiled_res"/*.flat 2>/dev/null)
if [ -n "$FLAT_FILES" ]; then
    $BUILD_TOOLS/aapt2 link -o "$OUT_DIR/base.apk" \
        -I "$ANDROID_JAR" \
        --manifest "$SRC/AndroidManifest.xml" \
        -A "$SRC/assets" \
        --auto-add-overlay \
        --java "$OUT_DIR/gen" \
        $FLAT_FILES
else
    $BUILD_TOOLS/aapt2 link -o "$OUT_DIR/base.apk" \
        -I "$ANDROID_JAR" \
        --manifest "$SRC/AndroidManifest.xml" \
        -A "$SRC/assets" \
        --auto-add-overlay \
        --java "$OUT_DIR/gen"
fi
mkdir -p "$OUT_DIR/gen"
echo "       资源链接完成"

# 6. 编译 Java
echo "[5/8] 编译 Java 源码..."
javac -source 1.8 -target 1.8 \
    -cp "$ANDROID_JAR:$ZXING_JAR" \
    -sourcepath "$OUT_DIR/gen" \
    -d "$OUT_DIR/java_classes" \
    "$SRC"/java/com/tktool/guofei/*.java "$OUT_DIR"/gen/com/tktool/guofei/R.java 2>/dev/null || \
javac -source 1.8 -target 1.8 \
    -cp "$ANDROID_JAR:$ZXING_JAR" \
    -d "$OUT_DIR/java_classes" \
    "$SRC"/java/com/tktool/guofei/*.java
echo "       编译完成"

# 7. D8 转换 dex
echo "[6/8] D8 转换 dex..."
$BUILD_TOOLS/d8 --lib "$ANDROID_JAR" --output "$OUT_DIR" \
    $(find "$OUT_DIR/java_classes" -name "*.class") \
    $(find "$OUT_DIR/zxing_classes" -name "*.class")
echo "       dex: $(ls -la "$OUT_DIR/classes.dex" | awk '{print $5}') bytes"

# 8. 打包签名
echo "[7/8] 打包签名..."
cd "$OUT_DIR"
cp base.apk unsigned.apk
$BUILD_TOOLS/aapt add unsigned.apk classes.dex
$BUILD_TOOLS/zipalign -f -p 4 unsigned.apk aligned.apk

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KEYSTORE_PASS" -keypass "$KEY_PASS" \
        -dname "CN=TKTool, OU=Dev, O=GuoFei, L=Shenzhen, ST=Guangdong, C=CN"
fi

$BUILD_TOOLS/apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:"$KEYSTORE_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --key-pass pass:"$KEY_PASS" \
    --out guofei_tool.apk \
    aligned.apk

cd "$SCRIPT_DIR"

echo "[8/8] 构建完成!"
echo ""
echo "========================================"
echo "  APK: $OUT_DIR/guofei_tool.apk"
echo "  大小: $(ls -la "$OUT_DIR/guofei_tool.apk" | awk '{print $5}') bytes"
echo "========================================"
