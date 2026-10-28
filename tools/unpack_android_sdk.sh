#!/usr/bin/env bash
# 从腾讯镜像手动铺 Android SDK（platform-tools / platform 35 / build-tools 35）
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/.toolchain"
DL="$TC/dl"
SDK="$TC/sdk"
mkdir -p "$SDK/platforms" "$SDK/build-tools"

unz(){ python -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$1" "$2"; }

if [ ! -d "$SDK/platform-tools" ]; then
  echo "== platform-tools"
  unz "$DL/platform-tools-latest-windows.zip" "$SDK"
fi

if [ ! -d "$SDK/platforms/android-35" ]; then
  echo "== platform android-35"
  rm -rf "$TC/tmpplat"; unz "$DL/platform-35_r01.zip" "$TC/tmpplat"
  top="$(ls "$TC/tmpplat" | head -1)"
  mv "$TC/tmpplat/$top" "$SDK/platforms/android-35"
  rm -rf "$TC/tmpplat"
fi

if [ ! -d "$SDK/build-tools/35.0.0" ]; then
  echo "== build-tools 35.0.0"
  rm -rf "$TC/tmpbt"; unz "$DL/build-tools_r35_windows.zip" "$TC/tmpbt"
  top="$(ls "$TC/tmpbt" | head -1)"
  mv "$TC/tmpbt/$top" "$SDK/build-tools/35.0.0"
  rm -rf "$TC/tmpbt"
fi

echo "== 结构:"
ls "$SDK"
echo "-- platforms:"; ls "$SDK/platforms"
echo "-- build-tools:"; ls "$SDK/build-tools"
echo "-- source.properties:"
head -4 "$SDK/platforms/android-35/source.properties" 2>/dev/null || echo "缺 source.properties!"
head -4 "$SDK/build-tools/35.0.0/source.properties" 2>/dev/null || echo "缺 build-tools source.properties!"