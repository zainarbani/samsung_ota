plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.sample.samsung.ota"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sample.samsung.ota"
        minSdk = 26
        targetSdk = 34
        multiDexEnabled = false
        versionName = version as String
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.shizukuapi)
    implementation(libs.shizukuprovider)
    compileOnly(project(":stub"))
}