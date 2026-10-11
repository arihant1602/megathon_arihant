plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Large build outputs (APK with bundled models) can be redirected, e.g. to a RAM disk: -PappBuildDir=/tmp/app-build
providers.gradleProperty("appBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }

// main's original model-zip import path (unused by the incident assistant); off unless asked for.
val bundleModels = providers.gradleProperty("bundleModels").orElse("false").map { it.toBoolean() }
val modelBundle = rootProject.layout.projectDirectory.file(
    providers.gradleProperty("modelBundlePath").orElse("tmp/reference-models/incident-asr-llm.zip").get()
)
val bundledAssets = layout.buildDirectory.dir("generated/bundledModelAssets")
val prepareBundledModels = tasks.register<Sync>("prepareBundledModels") {
    into(bundledAssets)
    if (bundleModels.get()) {
        from(modelBundle) {
            into("bundled-models")
            rename { "incident-asr-llm.zip" }
        }
        doFirst {
            check(modelBundle.asFile.isFile) {
                "Missing model bundle. Run python scripts/fetch_reference_models.py before building, or use -PbundleModels=false for an import-only APK."
            }
        }
    }
}
tasks.named("preBuild") {
    if (bundleModels.get()) {
        dependsOn(prepareBundledModels)
        doFirst {
            check(modelBundle.asFile.isFile) {
                "Missing model bundle. Run python scripts/fetch_reference_models.py before building, or use -PbundleModels=false."
            }
        }
    }
}

android {
    namespace = "org.seedhecode.incidentassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.seedhecode.incidentassistant"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0"
        if (providers.gradleProperty("nativeModels").orNull == "true") {
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    buildTypes {
        release {
            // Signed with the debug key so the release APK installs directly with `adb install` (hackathon build).
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    if (bundleModels.get()) sourceSets.getByName("main").assets.srcDir(bundledAssets)
    // All-in-one APK: -PmodelAssets=<dir containing models/manifest.json + model files> (see scripts/build_full_apk.sh).
    // The app unpacks them into files/models on first launch (ModelInstaller).
    providers.gradleProperty("modelAssets").orNull?.let { sourceSets.getByName("main").assets.srcDir(it) }
    androidResources { noCompress += "zip" }
    // Neural TTS runtime (scripts/fetch_sherpa_onnx.sh); without it the app speaks with the system TTS voice.
    rootProject.file("third_party/sherpa-onnx/jniLibs").takeIf { it.isDirectory }?.let { sourceSets.getByName("main").jniLibs.srcDir(it) }
    // The Hexagon skel must exist as a real file in nativeLibraryDir for the DSP loader (ADSP_LIBRARY_PATH).
    packaging { jniLibs.useLegacyPackaging = true }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
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
    if (providers.gradleProperty("snpeSdk").isPresent) implementation(project(":core:native-snpe"))

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
