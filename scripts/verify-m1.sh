#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"
incomplete=0

echo "== server tests =="
if [[ -n "${TEST_DB_URL:-}" ]]; then
  (cd server && mvn test)
else
  echo "INCOMPLETE: TEST_DB_URL is not set; PostgreSQL integration tests were not run."
  incomplete=1
  (cd server && mvn test -Dtest='!*SchemaConstraintTest')
fi

echo "== Android JVM tests =="
if [[ -x "android/gradlew" ]] && [[ -n "${ANDROID_HOME:-}" || -n "${ANDROID_SDK_ROOT:-}" ]]; then
  (cd android && ./gradlew :core:model:test :core:sync:test)
else
  echo "INCOMPLETE: Android SDK is not configured; Android JVM/instrumented checks were not run."
  incomplete=1
fi

echo "== Android device checks =="
if [[ -x "android/gradlew" ]] && [[ -n "${ANDROID_HOME:-}" || -n "${ANDROID_SDK_ROOT:-}" ]]; then
  (cd android && ./gradlew :app:connectedDebugAndroidTest \
    :feature:editor:connectedDebugAndroidTest \
    :feature:timeline:connectedDebugAndroidTest \
    :feature:couple:connectedDebugAndroidTest \
    :core:database:connectedDebugAndroidTest)
else
  echo "INCOMPLETE: Android SDK/emulator is not configured; device checks were not run."
  incomplete=1
fi

if [[ "$incomplete" -ne 0 ]]; then
  echo "M1 verification incomplete: required environment checks are missing."
  exit 2
fi

echo "M1 verification passed."
