plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val bundleModels = providers.gradleProperty("bundleModels").orElse("true").map { it.toBoolean() }
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
    if (bundleModels.get()) sourceSets.getByName("main").assets.srcDir(bundledAssets)
    androidResources { noCompress += "zip" }
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
