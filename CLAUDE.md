# PHOTO SWEEP — project brief

Read this whole file before writing anything. It governs every stage from Stage 5.5 onward.

# Verification rules — non-negotiable

You may never write, in any form:
  "verified", "matches the design", "pixel-perfect", "looks correct",
  "should render as expected", "visually confirmed", "I've confirmed"

You have no eyes. You compare against your mental model, not rendered pixels.
Visual state is decided by Simo on a physical device, and only there.

When you finish a stage you must print:
  - the exact commands you ran, verbatim
  - their exit codes, as integers
  - for tests: the count of tests run / passed / failed
  - for builds: the APK path and its size in bytes

Never describe a result. Print it.
If a command failed, print the failure. Do not summarise it as "minor issue".

STOP means STOP. Do not begin the next stage. Do not "get a head start".

## PROJECT FACTS

- Package / applicationId: `com.simobr.photosweep` (studio: Simobr Studio, matching the
  existing `com.simobr.aurafy` and `com.simobr.donotblink` convention in Google Play Console).
- App name: "Photo Sweep".
- Single module `:app`. Kotlin, Jetpack Compose, native. No multi-module split.
- `minSdk = 30` is deliberate and is not to be lowered: `MediaStore.createTrashRequest()` and
  `createDeleteRequest()` arrived in API 30. Below that the app would need a second, legacy,
  `WRITE_EXTERNAL_STORAGE` destructive path with no system trash. We do not ship that.
- `targetSdk = 36`, `compileSdk = 36`. Google Play requires target API 36 for new submissions
  from 31 Aug 2026 and this app submits after that date.
- Persistence: Room (`PhotoSweepDatabase`, schemas exported to `app/schemas`) + DataStore
  Preferences. Room schemas are committed — a migration without an exported schema is a
  migration nobody can test.
- The app never mutates MediaStore through the content resolver. Every trash, restore and
  delete goes through a `MediaStore.create*Request` IntentSender that the *system* executes
  after showing its own dialog. `SourceHygieneTest` enforces this.
- `design/` holds the mockups. Never treat a mockup as verification of rendered output.
- `RELEASE.md` is the submission checklist. `SCREENS.md` holds the measurable per-screen
  blocks. Both are updated in the same commit as the change they describe.

## PINNED TOOLCHAIN

Recorded so later stages do not drift. Change only with a reason.

| Thing | Version |
| --- | --- |
| Gradle wrapper | 8.14.3 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / compose-compiler plugin | 2.3.20 |
| KSP | 2.3.11 (built against Kotlin 2.3.20 exactly) |
| Compose BOM | 2026.06.01 (newest line that still builds at compileSdk 36; Compose 1.12 demands compileSdk 37 + AGP 9.1) |
| androidx.core | 1.18.0 (1.19.0 raises minCompileSdk to 37) |
| androidx.lifecycle | 2.10.0 (2.11.0 raises minCompileSdk to 37) |
| Room | 2.8.4 |
| JDK (local) | 17 (only JDK on this machine) |
| Java/Kotlin bytecode target | 17 |

Kotlin 2.x: the Compose compiler is the `org.jetbrains.kotlin.plugin.compose` Gradle plugin.
Do NOT set `composeOptions.kotlinCompilerExtensionVersion` — that is the pre-Kotlin-2.0 way
and it will fail.

All versions live in `gradle/libs.versions.toml`. Never hardcode a version in a
`build.gradle.kts`.

## DEBUG-ONLY CODE

The gallery seeder and the pile-scope filter are development tools and must not reach a
release build. They live in `app/src/debug/` (source and resources), with a no-op
`DebugPileTools` stub in `app/src/release/`. `DebugIsolationTest` enforces the split.

## AD SDK NOTE

`play-services-ads` ships a `MobileAdsInitProvider` that throws at process start when
`com.google.android.gms.ads.APPLICATION_ID` meta-data is absent from the manifest. The debug
build uses Google's public test App ID (`ca-app-pub-3940256099942544~3347511713`). The
release build reads `photosweep.admobAppId` from `local.properties` or
`~/.gradle/gradle.properties` and **fails the build** if it is absent. See `RELEASE.md`.
