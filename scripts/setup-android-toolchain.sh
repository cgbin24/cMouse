#!/bin/bash
# cMouse Android 构建工具链自动安装（JDK17 + Gradle + Android SDK 35）
set -e
TOOLS="$HOME/cmouse-tools"
mkdir -p "$TOOLS" && cd "$TOOLS"

echo "[1/5] 下载 JDK 17 (aarch64)..."
curl -sL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/macos/aarch64/jdk/hotspot/normal/eclipse"
mkdir -p jdk17 && tar xzf jdk17.tar.gz -C jdk17 --strip-components=1
echo "JDK OK: $(jdk17/Contents/Home/bin/java -version 2>&1 | head -1)"

echo "[2/5] 下载 Gradle 8.10.2..."
curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip"
unzip -oq gradle.zip
"$TOOLS/gradle-8.10.2/bin/gradle" --version | head -5

echo "[3/5] 下载 Android cmdline-tools..."
curl -sL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
mkdir -p android-sdk/cmdline-tools
unzip -oq cmdtools.zip -d android-sdk/cmdline-tools
rm -rf android-sdk/cmdline-tools/latest
mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest

export JAVA_HOME="$TOOLS/jdk17/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
SDKM="$TOOLS/android-sdk/cmdline-tools/latest/bin/sdkmanager"

echo "[4/5] 接受 SDK 许可协议..."
yes | "$SDKM" --sdk_root="$TOOLS/android-sdk" --licenses > /dev/null 2>&1 || true

echo "[5/5] 安装 SDK 组件（platform 35 / build-tools 35 / platform-tools）..."
yes | "$SDKM" --sdk_root="$TOOLS/android-sdk" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo "SETUP-DONE"
