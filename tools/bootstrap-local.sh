#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="${ANDROID_TOOLCHAIN_DIR:-/workspace/android-toolchain}"
mkdir -p "$TASK_ROOT/sdk/cmdline-tools" "$TASK_ROOT/jdk17" "$TASK_ROOT/gradle-home"
if [ ! -x "$TASK_ROOT/jdk17/bin/javac" ]; then
  tar -xzf "$TASK_ROOT/downloads/jdk17.tar.gz" --strip-components=1 -C "$TASK_ROOT/jdk17"
  if [ -f /etc/ssl/certs/java/cacerts ]; then
    cp /etc/ssl/certs/java/cacerts "$TASK_ROOT/jdk17/lib/security/cacerts"
  fi
fi
if [ ! -x "$TASK_ROOT/sdk/cmdline-tools/12.0/bin/sdkmanager" ]; then
  unzip -q "$TASK_ROOT/downloads/commandline.zip" -d "$TASK_ROOT/sdk/cmdline-tools"
  mv "$TASK_ROOT/sdk/cmdline-tools/cmdline-tools" "$TASK_ROOT/sdk/cmdline-tools/12.0"
fi
if [ ! -x "$TASK_ROOT/gradle-8.9/bin/gradle" ]; then
  unzip -q "$TASK_ROOT/downloads/gradle.zip" -d "$TASK_ROOT"
fi
export JAVA_HOME="$TASK_ROOT/jdk17"
export ANDROID_HOME="$TASK_ROOT/sdk"
export GRADLE_USER_HOME="$TASK_ROOT/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/12.0/bin:$ANDROID_HOME/platform-tools:$PATH"
if [ -n "${HTTPS_PROXY:-}" ]; then
  TASK_PROXY_HOST=$(python3 -c 'import os,urllib.parse; print(urllib.parse.urlparse(os.environ["HTTPS_PROXY"]).hostname)')
  TASK_PROXY_PORT=$(python3 -c 'import os,urllib.parse; print(urllib.parse.urlparse(os.environ["HTTPS_PROXY"]).port or 80)')
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttps.proxyHost=$TASK_PROXY_HOST -Dhttps.proxyPort=$TASK_PROXY_PORT -Dhttp.proxyHost=$TASK_PROXY_HOST -Dhttp.proxyPort=$TASK_PROXY_PORT"
fi
set +o pipefail
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses > "$TASK_ROOT/licenses.log" 2>&1
set -o pipefail
sdkmanager --sdk_root="$ANDROID_HOME" --install 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
