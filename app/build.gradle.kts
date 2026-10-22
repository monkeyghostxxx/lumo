plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "mx.lumo.auto"
    compileSdk = 35

    defaultConfig {
        applicationId = "mx.lumo.auto"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firma con la llave debug para que el APK sea instalable sin configurar nada más.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")

    // Android Auto (Car App Library)
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
}
