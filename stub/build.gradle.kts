plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.app.sample.stub"
    compileSdk = 34

    defaultConfig {
        multiDexEnabled = false
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
