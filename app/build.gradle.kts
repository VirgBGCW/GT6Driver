plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gt6driver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gt6driver"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // ✅ TEMP: use the debug signing config so the APK is installable
            signingConfig = signingConfigs.getByName("debug")
        }
        // (debug is created automatically; nothing to add)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Version-catalog deps
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Core
    implementation("androidx.core:core:1.13.1")

    // UI
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Images (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Printing
    implementation("androidx.print:print:1.0.0")
    // QR Code
    implementation ("com.google.zxing:core:3.5.3")

    // ✅ Add this for androidx.preference.*
    implementation("androidx.preference:preference:1.2.1")
    // (Optional) KTX helpers if you start using Kotlin APIs:
    // implementation("androidx.preference:preference-ktx:1.2.1")

    // Background Worker
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}


