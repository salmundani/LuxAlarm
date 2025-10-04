./gradlew assembleRelease && \
  source .env && \
  apksigner sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" --ks-pass pass:"$KEYSTORE_PASS" --key-pass pass:"$KEY_PASS" \
    --out luxalarm.apk app/build/outputs/apk/release/app-release-unsigned.apk && \
  apksigner verify --verbose --print-certs luxalarm.apk
