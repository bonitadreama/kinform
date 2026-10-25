plugins { id("com.android.application") }

android {
    namespace = "org.kinform.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.kinform.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 26
        versionName = "0.8.15"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }
}
dependencies { implementation("com.squareup.okhttp3:okhttp:4.12.0") }
