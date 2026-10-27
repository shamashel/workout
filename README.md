# Trades Prep

A private, one-person workout log. It stores strength sessions on the phone, suggests the next weight, target reps, and target RIR after each session, and can write the session duration to Health Connect. There is no account, no cloud, and no analytics. The app does not request a network permission.

Year 1 (Day A / B / C, slots A1–C5) is the plan the app runs. Later years are notes you can edit. The progression rules live in the `engine` module, which is plain Kotlin with no Android dependencies. Ambiguous spots in the plan are recorded in [DECISIONS.md](DECISIONS.md).

## Requirements

- JDK 17 or newer (the project targets JVM 17; JDK 21 is fine)
- Android SDK platform 36 and build-tools 36.0.0
- A device or an `adb` install. `assembleDebug` uses the debug keystore Android Gradle Plugin creates on that machine. Release APKs use the committed personal-use key described below, so they upgrade over each other.

## SDK

Point the build at your SDK. Either export `ANDROID_HOME` or write `local.properties` (that file is gitignored):

```
sdk.dir=/path/to/android-sdk
```

Command-line tools, if you do not already have the SDK:

```bash
# After unpacking command-line tools into $ANDROID_HOME/cmdline-tools/latest
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

## Build

```bash
./gradlew assembleDebug
./gradlew test
./gradlew assembleRelease
```

`assembleRelease` signs with the personal-use keystore. Pass `-Papp.versionCode` and `-Papp.versionName` to match a tag, for example `-Papp.versionCode=1000 -Papp.versionName=0.1.0`.

`test` runs the engine unit tests (the worked examples and the overload rules) and the app unit tests.

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Install a debug build from this machine

Enable USB debugging, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is signed with this machine's debug key. It will not upgrade an install that came from a GitHub release. Uninstall that copy first, or install the release APK instead. Export a JSON backup before uninstalling if you want to keep the log.

The app id is `com.shamashel.workout`. On first launch it asks you to read the safety line: “Not medical advice. Check with your doctor before ramping up.” The same line is in Settings.

Health Connect is optional. In Settings, turn on “Write sessions to Health Connect” and grant the exercise-session write. Sets and reps are not sent. If Health Connect is missing or needs an update, the session stays in the app.

## Download a release on the phone

After a tag is published, open this page in the phone's browser:

https://github.com/shamashel/workout/releases

Open the release (for example `v0.1.0`) and download `trades-prep-v0.1.0.apk`.

Android will refuse the file until the browser is allowed to install apps. In Chrome that is Settings, Apps, Chrome, Install unknown apps, Allow. Other browsers have the same switch under their own app name. Then tap the downloaded APK and install it.

A newer tag installs over the previous release and keeps the on-device log, because every release APK is signed with the same key and carries a higher `versionCode`. A local debug build is a different signature. If Android says the app was not installed, the phone already has a copy signed by another key. Export a backup from the app, uninstall it, and install the release APK again.

Health Connect permission is requested by the app, not by the installer. After install or upgrade, open Trades Prep, go to Settings, and grant the exercise-session write if you want swims and strength sessions on Health Connect.

## Cut a release

Tags look like `v0.1.0`. Pushing one runs tests, builds a signed APK, and creates a GitHub Release with that APK and generated notes.

```bash
git checkout main
git pull origin main
git tag v0.1.0
git push origin v0.1.0
```

Use a higher tag next time (`v0.1.1`, then `v0.2.0`). The workflow turns `vMAJOR.MINOR.PATCH` into `versionCode = MAJOR * 1000000 + MINOR * 1000 + PATCH`, so `v0.1.0` is versionCode 1000 and `v0.1.1` is 1001. A tag that does not match that pattern fails the build. `v0.0.0` is rejected because versionCode would be 0.

To build an APK without publishing a release, open the Actions tab, choose the Android workflow, and run it manually. The APK is a workflow artifact only. Its versionCode stays 1, so it installs on a phone that does not already have a tagged release. It will not replace a tagged install.

## Signing key

Release builds are signed with `app/signing/trades-prep-debug.keystore`, alias `trades-prep`, store password and key password `trades-prep`. That password is public. The key is only for this personal app. Do not reuse it for anything else.

To sign releases with a private key instead, create one and store it in repository secrets. Do not commit that file.

```bash
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore trades-prep-release.keystore \
  -alias trades-prep \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 'choose-a-password' \
  -keypass 'choose-a-password' \
  -dname "CN=Your Name, O=Personal, C=US"
base64 -w 0 trades-prep-release.keystore
```

Set all four repository secrets, or leave all four unset. A partial set fails the build.

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | The `base64 -w 0` output |
| `RELEASE_STORE_PASSWORD` | The store password |
| `RELEASE_KEY_ALIAS` | `trades-prep` (or the alias you chose) |
| `RELEASE_KEY_PASSWORD` | The key password |

APKs signed with that private key do not upgrade over APKs signed with the committed key. Pick one key before the first install you care about keeping.

Settings also holds plate increments, the dumbbell list, micro-plates, and the skip-deload switch. Export and import use the system file picker and a local JSON file.

## Layout

- `engine/` — progressive-overload rules and JVM tests
- `app/` — Compose UI, Room database, Health Connect writer
