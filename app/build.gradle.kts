plugins {
    id("com.android.application")
}

android {
    namespace = "com.matrixevreni.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    signingConfigs {
        create("stable") {
            storeFile = file("matrixevreni-clean.jks")
            storePassword = "varantmatik"
            keyAlias = "varantmatik-lite"
            keyPassword = "varantmatik"
        }
    }

    defaultConfig {
        applicationId = "com.matrixevreni.v2"
        minSdk = 24
        targetSdk = 35
        versionCode = 200
        versionName = "2.0.0"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
