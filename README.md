# PyPocket

PyPocket is an offline-first Python IDE and beginner learning app for Android. It is built with Kotlin, Jetpack Compose, and the embedded CPython runtime provided by [Chaquopy](https://chaquo.com/chaquopy/).

## Features

- Dark, phone-sized editor with Python syntax colors, line numbers, indentation, tabs, save, undo, and comment controls.
- Custom PyPocket branding with a matching Android adaptive launcher icon.
- Runs Python 3.11 in the app. print(), exceptions and tracebacks, and interactive input() are handled by the embedded interpreter.
- Create, edit, rename, save, close, and reopen .py files in persistent app-private storage.
- Eight offline lessons with editable examples, exercises, and on-device progress.
- Output console can be collapsed and resized. Python runs on a worker thread and can be stopped at Python instruction boundaries.
- No account, analytics, ads, telemetry, or Android network permission.

## Build

Requirements: JDK 17, Android SDK platform 35 and build tools 35.0.0. The Gradle wrapper downloads the pinned Gradle 8.9 distribution. Chaquopy's build plugin and Android dependencies are resolved during a build; the installed app does not need a network connection.

    ./gradlew assembleDebug

The debug APK is created at app/build/outputs/apk/debug/app-debug.apk. Run local unit tests with:

    ./gradlew testDebugUnitTest

GitHub Actions builds the debug APK on pushes, pull requests, or manual workflow dispatch and uploads it as the PyPocket-debug-apk artifact.

## Install the APK

Download app-debug.apk from the GitHub Actions artifact, transfer it to the Android device, and open it. Android may ask you to allow installation from the app used to open the file. You can also install a connected device with:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

The debug application ID is dev.pypocket.debug.

## Offline storage and privacy

The IDE stores Python files under the app's private files directory and lesson progress in private app preferences. It does not request Internet access or expose project files to other apps. Android removes this data when PyPocket is uninstalled.

## Runtime limitations

- PyPocket embeds CPython 3.11. The standard library is available, but some Android-incompatible modules are not, including tkinter, turtle, curses, grp, and multiprocessing process APIs. GUI programs cannot open desktop windows.
- Third-party Python packages are not preinstalled. The app is intentionally offline at runtime, so there is currently no package installer.
- Stop requests are checked between Python instructions. A script blocked inside a native extension or a long native system call may not stop immediately.
- Python code runs in the app process with the app's private-storage access. Only run code you trust.

## License

PyPocket is distributed under the MIT License. See LICENSE.
