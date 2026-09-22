plugins {
    id("com.android.application")
}

android {
    namespace = "com.matrixevreni.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.matrixevreni.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 20
        versionName = "2.0.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
