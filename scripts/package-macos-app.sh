#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DERIVED_DATA="${ROOT}/build/DerivedData"
APP_PATH="${DERIVED_DATA}/Build/Products/Release/BmuAttendance.app"
ZIP_PATH="${ROOT}/build/BmuAttendance-macOS-apple-silicon.zip"

cd "${ROOT}/IOS"

xcodebuild \
  -project BmuAttendance.xcodeproj \
  -scheme BmuAttendanceMac \
  -configuration Release \
  -destination 'generic/platform=macOS' \
  -derivedDataPath "${DERIVED_DATA}" \
  -allowProvisioningUpdates \
  build

rm -f "${ZIP_PATH}"
ditto -c -k --sequesterRsrc --keepParent "${APP_PATH}" "${ZIP_PATH}"

echo "Created ${ZIP_PATH}"
echo "Native macOS app (target BmuAttendanceMac). Install: unzip, move BmuAttendance.app to Applications, then open."
