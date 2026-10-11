plugins { id("com.android.library") }

// The Qualcomm SDK is not redistributable; point -PsnpeSdk (or gradle.properties) at a local QAIRT 2.47 install.
val snpeSdk = providers.gradleProperty("snpeSdk").map { file(it) }.get()
val snpeLibs = layout.buildDirectory.dir("generated/snpeJniLibs")
val copySnpeLibs = tasks.register<Sync>("copySnpeLibs") {
    from(snpeSdk.resolve("lib/aarch64-android")) { include("libSNPE.so", "libSnpeHtpV79Stub.so") }
    // The skel runs on the Hexagon DSP; the app points ADSP_LIBRARY_PATH at its native lib dir.
    from(snpeSdk.resolve("lib/hexagon-v79/unsigned")) { include("libSnpeHtpV79Skel.so") }
    into(snpeLibs.map { it.dir("arm64-v8a") })
}
tasks.named("preBuild") { dependsOn(copySnpeLibs) }

android {
    namespace = "org.seedhecode.nativesnpe"
    compileSdk = 34
    ndkVersion = providers.gradleProperty("ndkVersion").orElse("27.2.12479018").get()
    defaultConfig {
        minSdk = 23
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared", "-DSNPE_ROOT=${snpeSdk.absolutePath}") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = providers.gradleProperty("cmakeVersion").orElse("3.22.1").get() } }
    sourceSets.getByName("main").jniLibs.srcDir(snpeLibs)
}
