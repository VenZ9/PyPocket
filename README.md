# PyPocket

PyPocket is an offline-first Android Python learning IDE built with Kotlin and Jetpack Compose.

## Current milestone

Milestone 1 is the UI/build foundation:
- VS Code-inspired dark editor workspace
- Editable code field with line numbers
- Output panel and Run affordance
- Editor, Projects, and Learn navigation
- Starter lesson and sample code
- GitHub Actions debug APK workflow

**Python execution is not implemented yet.** The Run action currently displays a clear placeholder. Embedded CPython integration is milestone 2.

## Build

Requirements: JDK 17 and Gradle 8.9.

```bash
gradle --no-daemon assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions builds the debug APK on pushes, pull requests, or manual workflow dispatch and publishes it as an artifact.

## Offline behavior

The app does not request network permission. Android build dependencies are downloaded during CI/build; they are not required at runtime. Python runtime and lesson persistence are planned for subsequent milestones.
