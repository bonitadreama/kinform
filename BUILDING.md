# Build from source

Use Android Studio, or a terminal with JDK 17 or newer supported by Gradle 9.1.0. Install Android SDK Platform 36 and Build-Tools 36.0.0 through the SDK Manager. Set `JAVA_HOME` and `ANDROID_HOME`, or let Android Studio create your ignored `local.properties` SDK path.

The repository pins Android Gradle Plugin 9.0.1 and the Gradle 9.1.0 wrapper, including the distribution SHA-256. Dependencies download on the first build. No provider key is needed to compile or run the regression tests.

Windows PowerShell, from the repository root:

```powershell
.\gradlew.bat :checks:check :app:assembleDebug :app:lintDebug
```

macOS/Linux:

```sh
sh ./gradlew :checks:check :app:assembleDebug :app:lintDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use application ID `org.kinform.app.dev`, so they can coexist with the original `org.kinform.app` app. Their data stores are separate. They do not automatically inherit characters or keys. Use an explicit character export/import if desired; imports are copies.

Android creates a local debug signing key. Keep keys private. Debug signing is for development, not a public release identity. Never uninstall or clear an existing app to work around a signature mismatch. Updating an existing app requires its original application ID and signing key. This source package includes neither the original developer key nor personal APKs.

For the optional backend tests, install Node.js 22 or newer:

```sh
cd backend
npm ci
npm test
```

These tests mock the provider and do not need paid API requests. Do not run `smoke:live` casually: it uses locally configured credentials and makes a real provider request.

See [Android build requirements](https://developer.android.com/build/releases/agp-9-0-0-release-notes) and [Gradle Java compatibility](https://docs.gradle.org/9.1.0/userguide/compatibility.html).
