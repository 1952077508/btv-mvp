#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_SERVER_DIR="$PROJECT_DIR/apk-server/apk"

echo "=== Building Android APK ==="
cd "$PROJECT_DIR/android"
./gradlew assembleDebug

APK_FILE=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
if [ -z "$APK_FILE" ]; then
    echo "ERROR: APK not found"
    exit 1
fi

echo "=== Copying APK to apk-server ==="
cp "$APK_FILE" "$APK_SERVER_DIR/BTVSync-latest.apk"
echo "APK copied: $APK_SERVER_DIR/BTVSync-latest.apk"

echo ""
echo "=== Done ==="
echo "APK download URL: http://<server-ip>:8080/BTVSync-latest.apk"
echo ""
echo "To start services:"
echo "  docker compose up -d"
