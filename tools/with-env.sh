#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="${ANDROID_TOOLCHAIN_DIR:-/workspace/android-toolchain}"
export JAVA_HOME="${JAVA_HOME:-$TASK_ROOT/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$TASK_ROOT/sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TASK_ROOT/gradle-home}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
if [ -n "${HTTPS_PROXY:-}" ]; then
    TASK_PROXY_HOST=$(python3 -c 'import os,urllib.parse; print(urllib.parse.urlparse(os.environ["HTTPS_PROXY"]).hostname)')
    TASK_PROXY_PORT=$(python3 -c 'import os,urllib.parse; print(urllib.parse.urlparse(os.environ["HTTPS_PROXY"]).port or 80)')
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttps.proxyHost=$TASK_PROXY_HOST -Dhttps.proxyPort=$TASK_PROXY_PORT -Dhttp.proxyHost=$TASK_PROXY_HOST -Dhttp.proxyPort=$TASK_PROXY_PORT"
fi
exec "$@"
