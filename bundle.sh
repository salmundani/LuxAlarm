#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

./gradlew bundleRelease

set -a
source .env
set +a

jarsigner -keystore "$KEYSTORE" -storepass:env KEYSTORE_PASS -keypass:env KEY_PASS \
  -signedjar luxalarm.aab app/build/outputs/bundle/release/app-release.aab "$ALIAS"
jarsigner -verify -verbose:summary -certs luxalarm.aab
