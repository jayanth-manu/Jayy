plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".mapper"
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    ndkVersion = "26.3.11579264"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.coroutines)
    implementation(libs.dexlib2)
    testImplementation(libs.junit)
}