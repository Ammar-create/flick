# AGENTS.md — flick

Read this before touching anything in this repo. Rules here are not preferences.

## 1. Build rules (hard)

- **NEVER build locally.** This project is developed on a Lenovo Tab M9, 4 GB RAM, inside a
  PRoot sandbox. Spawning the Gradle daemon drops free RAM below 500 MB and Android's LMK
  kills the session mid-conversation. If it ever happens by accident:
  `killall -9 java 2>/dev/null`
- **All builds go to GitHub Actions.** A full build is 13-16 minutes. Poll with
  `timeout: 180` and an internal loop, never with 20-30s tool loops.
- **arm64-v8a only.** One APK, never all 8 ABI/flavour combinations:
  `./gradlew :app:assembleDevDebug -Plastchat.abi.splits=true --no-parallel --stacktrace`
  Upload path: `app/build/outputs/apk/dev/debug/*arm64-v8a*.apk`
- Keep `kotlin.daemon.jvmargs=-Xmx4096m -XX:+UseParallelGC` in `gradle.properties`.
- CI needs JDK 21 (`temurin`). Add `oven-sh/setup-bun@v2` only if the module graph grows a
  web-ui Bun task; this project does not have one.

## 2. Architecture rules

- **The host is not the features.** Anything that can be a plugin must be a plugin. If a
  feature can only be done by editing the host, that is a signal the host is missing a
  bridge call, not a signal to hardcode the feature.
- **Plugins are text.** JavaScript + a metadata header, one file per feature. Never compiled
  Kotlin/DEX. A DEX plugin would mean a 13-16 minute CI cycle per feature, which defeats the
  entire point of the project.
- **The bridge is the API surface.** Adding a bridge call is a breaking-change-grade decision:
  bump `@api`, document it in `docs/SPEC.md`, keep old calls working.
- **Trigger config is data.** Gesture zones live in `triggers.json`, not in code.

## 3. Security rules

- Extensions run inside the host process, and the host is an accessibility service. That means
  an extension inherits the ability to read every screen on the device.
- Therefore: **only extensions authored in this repo are loaded.** No third-party repos, no
  community extensions, no hand-me-down plugins. If a third-party source ever needs supporting,
  it needs signing and a sandbox first.
- Never load a plugin from an app-writable path without verifying its hash against `repo.json`.

## 4. House style

- Zero emojis anywhere: code, UI labels, toasts, dialogs, commit messages.
- Kotlin, Compose, Material 3. Icons from the project's own icon set, not emoji.
- Lowercase paths, short names, no spaces.

## 5. Failure log

Append every gotcha here as it happens, with the date. This file is the project's immune system.

- 2026-10-13 — android-actions/setup-android@v3 fails on ubuntu-latest because it invokes deprecated sdkmanager tools package. GitHub runners already have Android SDK preinstalled at $ANDROID_HOME; setup-android step must be omitted.
