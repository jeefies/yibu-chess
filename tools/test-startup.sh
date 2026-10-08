#!/usr/bin/env bash
set -euo pipefail
TASK_PROJECT=$(cd "$(dirname "$0")/.." && pwd)
cd "$TASK_PROJECT"
tools/with-env.sh ./gradlew :app:testDebugUnitTest --tests cn.yibu.chess.RemoteGameViewModelTest
