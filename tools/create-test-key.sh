#!/usr/bin/env bash
set -euo pipefail
TASK_PROJECT=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$TASK_PROJECT/signing"
if [ ! -f "$TASK_PROJECT/signing/personal.jks" ]; then
  keytool -genkeypair -keystore "$TASK_PROJECT/signing/personal.jks" -storepass yibu-personal-test -keypass yibu-personal-test -alias yibu -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=YiBu Personal Test'
fi
