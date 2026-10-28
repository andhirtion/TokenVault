#!/usr/bin/env bash
# 用项目自带的工具链编译出 APK（不需要系统装 JDK / Android SDK / Android Studio）
#
#   bash tools/build_apk.sh            # debug
#   bash tools/build_apk.sh release    # release（已配 debug 签名，仅供自己装机）
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/.toolchain"
GRADLE="$TC/gradle-8.11.1/bin/gradle"
TASK="${1:-debug}"

[ -x "$TC/jdk/bin/java.exe" ] || { echo "缺 JDK，先跑 bash tools/unpack_toolchain.sh"; exit 1; }
[ -d "$TC/sdk/platforms/android-35" ] || { echo "缺 SDK，先跑 bash tools/unpack_android_sdk.sh"; exit 1; }
[ -x "$GRADLE" ] || [ -f "$GRADLE.bat" ] || { echo "缺 Gradle：$GRADLE"; exit 1; }

export JAVA_HOME="$TC/jdk"
export ANDROID_HOME="$TC/sdk"
export ANDROID_SDK_ROOT="$TC/sdk"
export PATH="$TC/jdk/bin:$PATH"
# 独立 Gradle 用户目录：避免落在全局 ~/.gradle 里被别的 Gradle 版本的
# native-platform 缓存污染（那会直接报 Failed to load native library）
export GRADLE_USER_HOME="$TC/gradle-home"
# 让 AGP 把 debug 签名材料放在项目内（$TC/.android），而不是写用户主目录的
# ~/.android/debug.keystore —— 那里常常有残留的 .lock 文件导致 AccessDenied
export ANDROID_PREFS_ROOT="$TC"
export ANDROID_SDK_HOME="$TC"
# 系统 %TEMP% 常常被安全软件或 ACL 搞成不可写，Kotlin 编译器在里头建 .alive
# 锁文件会直接 AccessDenied。把临时目录也收进项目内。
mkdir -p "$TC/tmp"
export TMPDIR="$TC/tmp"
export TMP="$(cygpath -w "$TC/tmp")"
export TEMP="$TMP"

# 让 AGP 知道 SDK 位置。注意必须用正斜杠：Java properties 里 \U 之类会被当成
# unicode 转义，Windows 反斜杠路径会直接炸掉（报“文件名、目录名或卷标语法不正确”）
printf 'sdk.dir=%s\n' "$(cygpath -m "$TC/sdk")" > "$ROOT/android/local.properties"

cd "$ROOT/android"
case "$TASK" in
  test|Test|TEST)
    "$GRADLE" --no-daemon testDebugUnitTest --console=plain
    exit $?
    ;;
  release|Release|RELEASE)
    "$GRADLE" --no-daemon -q assembleRelease
    OUT="app/build/outputs/apk/release/app-release.apk"
    ;;
  *)
    "$GRADLE" --no-daemon -q assembleDebug
    OUT="app/build/outputs/apk/debug/app-debug.apk"
    ;;
esac

echo ""
echo "== 产物:"
ls -lh "$ROOT/android/$OUT"
echo ""
echo "$ROOT/android/$OUT"