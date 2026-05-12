#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DERIVED_DATA="${ROOT}/build/DerivedData"
APP_PATH="${DERIVED_DATA}/Build/Products/Release-iphoneos/BmuAttendance.app"
ZIP_PATH="${ROOT}/build/BmuAttendance-macOS-apple-silicon.zip"

cd "${ROOT}/IOS"

xcodebuild \
  -project BmuAttendance.xcodeproj \
  -scheme BmuAttendance \
  -configuration Release \
  -destination 'platform=macOS,arch=arm64' \
  -derivedDataPath "${DERIVED_DATA}" \
  CODE_SIGNING_ALLOWED=NO \
  build

rm -f "${ZIP_PATH}"
ditto -c -k --sequesterRsrc --keepParent "${APP_PATH}" "${ZIP_PATH}"

echo "Created ${ZIP_PATH}"
