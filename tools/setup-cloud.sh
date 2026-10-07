#!/usr/bin/env bash
# Linux x86_64 bootstrap. All archives are pinned and checksum-verified.
set -euo pipefail
TASK_PROJECT=$(cd "$(dirname "$0")/.." && pwd)
TASK_ROOT="${ANDROID_TOOLCHAIN_DIR:-/workspace/android-toolchain}"
mkdir -p "$TASK_ROOT/downloads"
fetch() {
  local task_url="$1" task_file="$2" task_sha="$3"
  if [ ! -f "$task_file" ] || ! printf '%s  %s\n' "$task_sha" "$task_file" | sha256sum -c - > /dev/null 2>&1; then
    curl -fsSL --retry 3 "$task_url" -o "$task_file"
  fi
  printf '%s  %s\n' "$task_sha" "$task_file" | sha256sum -c -
}
fetch 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip' "$TASK_ROOT/downloads/commandline.zip" '2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258'
fetch 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz' "$TASK_ROOT/downloads/jdk17.tar.gz" '3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e'
fetch 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' "$TASK_ROOT/downloads/gradle.zip" 'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab'
bash "$TASK_PROJECT/tools/bootstrap-local.sh"
printf 'sdk.dir=%s/sdk\n' "$TASK_ROOT" > "$TASK_PROJECT/local.properties"
bash "$TASK_PROJECT/tools/with-env.sh" bash "$TASK_PROJECT/tools/create-test-key.sh"
printf '\nToolchain ready. Build with: tools/with-env.sh ./gradlew :core:test :app:assembleRelease\n'
