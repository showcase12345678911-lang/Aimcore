# AIMCORE — Android Phase 1

A lightweight aim-training and sensitivity companion for Android, built with Kotlin and Jetpack Compose.

## Features
- Black/crimson UI with bottom navigation
- 30-second tap-target practice session, score and timer
- DPI, sensitivity and reference-multiplier calculator
- Starter game profile directory

## Requirements
- Android Studio Hedgehog or newer, or AndroidIDE
- JDK 17
- Android SDK Platform 34
- Internet connection for initial Gradle dependency downloads

## Open and build
1. Extract this folder.
2. Open the `AIMCORE` folder in Android Studio.
3. Allow Gradle sync and install any requested SDK components.
4. Connect your POCO X8 Pro, or use Android Studio's device mirroring/emulator.
5. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. The debug APK is normally at `app/build/outputs/apk/debug/app-debug.apk`.

You can also run `./gradlew assembleDebug` after Android Studio has generated/downloaded the Gradle wrapper. This source archive intentionally omits the wrapper JAR; Android Studio can create it via its Gradle tooling.

## Compatibility
- minSdk 26
- targetSdk 34
- compileSdk 34
- Java/Kotlin target 17

## Scope and limitations
AIMCORE is a standalone training and settings companion. It does not inject touch input, read game memory, alter game files, or automate aiming in third-party games. Android does not provide ordinary applications with universal control of other games. Phase 1 training scores and slider values are kept in memory and reset when the app process is recreated; persistence is planned for a later phase.

## Project coordinates
- Namespace/application ID: `com.aimcore`
- Version: `1.0.0`
