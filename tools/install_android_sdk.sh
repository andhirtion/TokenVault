#!/usr/bin/env bash
# 安装 Android SDK 组件（platform-tools / platform 35 / build-tools 35）
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/.toolchain"
SDKM="$TC/sdk/cmdline-tools/latest/bin/sdkmanager.bat"

[ -f "$SDKM" ] || { echo "缺少 sdkmanager：$SDKM"; exit 1; }

export JAVA_HOME="$(cygpath -w "$TC/jdk")"
SDKW="$(cygpath -w "$TC/sdk")"
echo "JAVA_HOME=$JAVA_HOME"
echo "SDK_ROOT=$SDKW"

echo "== 接受许可协议 …"
for _ in $(seq 1 60); do echo y; done | "$SDKM" --sdk_root="$SDKW" --licenses >/dev/null 2>&1 || true

echo "== 安装组件 …"
"$SDKM" --sdk_root="$SDKW" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" 2>&1 | tail -8

echo "== 已装内容:"
ls "$TC/sdk"