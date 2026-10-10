plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.seedhecode.incidentassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.seedhecode.incidentassistant"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        if (providers.gradleProperty("nativeModels").orNull == "true") {
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:audio"))
    implementation(project(":core:inference"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    if (providers.gradleProperty("nativeModels").orNull == "true") {
        implementation(project(":core:native-whisper"))
        implementation(project(":core:native-llama"))
    }
}
