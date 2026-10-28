#!/usr/bin/env bash
# 解开 JDK 与 Android cmdline-tools（幂等）
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/.toolchain"
DL="$TC/dl"

echo "ROOT=$ROOT"

# ── JDK 17 ───────────────────────────────────────────────
if [ ! -x "$TC/jdk/bin/java.exe" ]; then
  echo "== 解压 JDK 17 …"
  rm -rf "$TC/jdkraw"
  python -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$DL/jdk17.zip" "$TC/jdkraw"
  inner="$(ls -d "$TC/jdkraw"/*/ | head -1)"
  mv "$inner" "$TC/jdk"
  rm -rf "$TC/jdkraw"
else
  echo "== JDK 已在"
fi

# ── cmdline-tools ────────────────────────────────────────
SDK="$TC/sdk"
if [ ! -d "$SDK/cmdline-tools/latest/bin" ]; then
  echo "== 解压 cmdline-tools …"
  rm -rf "$TC/clt"
  python -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$DL/cmdline-tools.zip" "$TC/clt"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TC/clt/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$TC/clt"
else
  echo "== cmdline-tools 已在"
fi

echo "== JAVA:"
"$TC/jdk/bin/java.exe" -version 2>&1 | head -2
echo "== SDK 目录:"
ls "$SDK/cmdline-tools/latest/bin" | head