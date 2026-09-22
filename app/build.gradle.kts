plugins {
    id("com.android.application")
}

android {
    namespace = "com.matrixevreni.app"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.matrixevreni.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 12
        versionName = "1.2.0-clean"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
