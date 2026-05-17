plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.remedium.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.remedium.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    aaptOptions {
        noCompress += "tflite"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../remedium-release.jks")
            storePassword = "remedium2026"
            keyAlias = "remedium"
            keyPassword = "remedium2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")

    implementation("com.google.mlkit:text-recognition:16.0.1")

    // LiteRT-LM for Gemma 4 inference (Day 2+)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")

    // Coroutines for background threading
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}