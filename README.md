# Trades Prep

A private, one-person workout log. It stores strength sessions on the phone, suggests the next weight, target reps, and target RIR after each session, and can write the session duration to Health Connect. There is no account, no cloud, and no analytics. The app does not request a network permission.

Year 1 (Day A / B / C, slots A1–C5) is the plan the app runs. Later years are notes you can edit. The progression rules live in the `engine` module, which is plain Kotlin with no Android dependencies. Ambiguous spots in the plan are recorded in [DECISIONS.md](DECISIONS.md).

## Requirements

- JDK 17 or newer (the project targets JVM 17; JDK 21 is fine)
- Android SDK platform 36 and build-tools 36.0.0
- A device or an `adb` install. Debug builds use the Android debug keystore that Android Gradle Plugin creates locally. No release keystore is checked in.

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
```

`test` runs the engine unit tests (the worked examples and the overload rules) and the app unit tests.

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Install

Enable USB debugging, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app id is `com.shamashel.workout`. On first launch it asks you to read the safety line: “Not medical advice. Check with your doctor before ramping up.” The same line is in Settings.

Health Connect is optional. In Settings, turn on “Write sessions to Health Connect” and grant the exercise-session write. Sets and reps are not sent. If Health Connect is missing or needs an update, the session stays in the app.

Settings also holds plate increments, the dumbbell list, micro-plates, and the skip-deload switch. Export and import use the system file picker and a local JSON file.

## Layout

- `engine/` — progressive-overload rules and JVM tests
- `app/` — Compose UI, Room database, Health Connect writer
