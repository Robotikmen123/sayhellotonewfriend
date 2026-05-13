#!/usr/bin/env bash
# Build a TrollStore-installable Persona.ipa.
#
# TrollStore accepts unsigned ad-hoc IPAs as long as the binary has a fake
# signature attached via `ldid -S`. No real codesign / provisioning profile
# required, which is exactly why we can ship this without an Apple Developer
# account.
#
# Required tools:
#   - xcodebuild (macOS, used by CI)
#   - xcodegen
#   - ldid    (brew install ldid OR linux: cargo install ldid_macho – CI has it)
#   - zip
#
# Required env (set by CI from repo secrets; for local builds, export them):
#   AGENT_ID, ELEVENLABS_API_KEY, BACKEND_URL, BACKEND_TOKEN

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

: "${AGENT_ID:=REPLACE_AGENT_ID}"
: "${ELEVENLABS_API_KEY:=REPLACE_EL_KEY}"
: "${BACKEND_URL:=https://persona.example.workers.dev}"
: "${BACKEND_TOKEN:=REPLACE_BACKEND_TOKEN}"
export AGENT_ID ELEVENLABS_API_KEY BACKEND_URL BACKEND_TOKEN

cd "$ROOT/ios"
echo "→ generating Xcode project"
xcodegen generate

echo "→ archiving (unsigned)"
xcodebuild \
    -project Persona.xcodeproj \
    -scheme Persona \
    -configuration Release \
    -destination "generic/platform=iOS" \
    -archivePath "$ROOT/build/Persona.xcarchive" \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGN_IDENTITY="" \
    AD_HOC_CODE_SIGNING_ALLOWED=YES \
    archive

APP="$ROOT/build/Persona.xcarchive/Products/Applications/Persona.app"
if [[ ! -d "$APP" ]]; then
    echo "build failed: $APP not found" >&2
    exit 1
fi

echo "→ fake-signing with ldid"
ldid -S "$APP/Persona"

echo "→ packaging IPA"
PAYLOAD="$ROOT/build/Payload"
rm -rf "$PAYLOAD"
mkdir -p "$PAYLOAD"
cp -R "$APP" "$PAYLOAD/"
cd "$ROOT/build"
rm -f Persona.ipa
zip -qr Persona.ipa Payload
rm -rf Payload Persona.xcarchive

echo "→ done: $ROOT/build/Persona.ipa"
ls -lh "$ROOT/build/Persona.ipa"
