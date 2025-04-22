import org.gradle.kotlin.dsl.annotationProcessor

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.videoplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.videoplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat.v161)
    implementation(libs.exoplayer)
    testImplementation(libs.junit)
    implementation(libs.androidx.core.ktx.v170)
    implementation(libs.glide)
    implementation(libs.colorpicker)
    implementation(libs.verticalseekbar)
    implementation(libs.zoomlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation (libs.retrofit)
    implementation(fileTree("libs") {
        include("*.jar", "*.aar")
    })

    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.github.AtifSayings:Animatoo:1.0.1")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") 
    implementation ("com.google.code.gson:gson:2.10.1")
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.androidx.media3.exoplayer)
    androidTestImplementation(libs.androidx.espresso.core)
}