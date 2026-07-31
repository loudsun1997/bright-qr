# Releasing Bright QR

Bright QR uses one permanent Java keystore to establish the Android application's
identity. Every public update must be signed by the same key. Losing the key means
the existing installation can no longer be updated; exposing it allows someone
else to produce an APK Android accepts as an update.

The keystore and passwords must never be committed to this repository. The
included release workflow reconstructs the keystore temporarily from encrypted
GitHub Actions secrets, signs the APK, verifies the result, publishes a SHA-256
checksum, and deletes the runner afterward.

The expected release-certificate SHA-256 fingerprint is:

```text
C9:B8:8C:05:1A:66:54:2C:AE:05:49:73:CE:76:15:BE:
DB:26:13:4C:C2:B7:3A:5E:51:49:D8:CE:01:81:50:46
```

The workflow compares every release against the normalized value in
`docs/release-certificate-sha256.txt` and stops if a different key is supplied.

## One-time secret setup

Create a dedicated RSA release key with JDK `keytool`. Use unique, randomly
generated passwords and keep the resulting keystore in at least two secure backup
locations.

Configure these GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `BRIGHT_QR_KEYSTORE_BASE64` | Base64 representation of the complete `.jks` file |
| `BRIGHT_QR_STORE_PASSWORD` | Keystore password |
| `BRIGHT_QR_KEY_ALIAS` | Alias inside the keystore; this project uses `bright-qr` |
| `BRIGHT_QR_KEY_PASSWORD` | Private-key password |

On macOS, encode the keystore without line wrapping using:

```shell
base64 -i /secure/path/bright-qr-release.jks | pbcopy
```

Add each value at **Repository settings → Secrets and variables → Actions**.
GitHub encrypts repository secrets and masks them from workflow logs.

## Local signed build

Export the four value variables plus the keystore path, then run:

```shell
export BRIGHT_QR_KEYSTORE_PATH=/secure/path/bright-qr-release.jks
export BRIGHT_QR_STORE_PASSWORD='your-store-password'
export BRIGHT_QR_KEY_ALIAS='bright-qr'
export BRIGHT_QR_KEY_PASSWORD='your-key-password'
./gradlew clean test lintRelease assembleRelease
```

The signed output is `app/build/outputs/apk/release/app-release.apk`. If none of
the signing variables are present, local release builds remain unsigned. If only
some are present, Gradle stops instead of accidentally producing the wrong build.

## Publish a version

1. Update `versionCode` and `versionName` in `app/build.gradle`.
2. Commit the version change and merge it to `main`.
3. Create a matching annotated tag, such as `v0.1.0` for version `0.1.0`.
4. Push the tag.
5. Confirm **Publish signed APK** succeeds in GitHub Actions.
6. Install the APK from the GitHub Release and perform the physical HDR checks.

The workflow refuses to publish if the tag and `versionName` disagree. Release
assets use the stable names `bright-qr.apk` and `bright-qr.apk.sha256`, allowing
the README's latest-download link to remain unchanged between versions.

## Recovery and rotation

- Back up the keystore separately from its passwords.
- Record its SHA-256 certificate fingerprint and compare it for every release.
- Do not send the keystore through chat, email, issues, or pull requests.
- Do not replace the key merely because a password changes.
- If compromise is suspected, stop publishing and assess Android's supported key
  rotation path before producing another APK.
