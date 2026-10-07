#!/usr/bin/env bash
set -euo pipefail
TASK_PROJECT=$(cd "$(dirname "$0")/.." && pwd)
cd "$TASK_PROJECT"
tools/with-env.sh python3 tools/test-native.py
cp artifacts/native-probe/libyibu_stockfish_host.so artifacts/native-probe/libyibu_stockfish.so
tools/with-env.sh ./gradlew :app:testDebugUnitTest --tests cn.yibu.chess.StartupTest -PstartupNativeDir="$TASK_PROJECT/artifacts/native-probe"
