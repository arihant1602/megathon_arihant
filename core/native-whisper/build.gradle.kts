plugins { id("com.android.library") }
android {
    namespace = "org.seedhecode.nativewhisper"
    compileSdk = 34
    ndkVersion = providers.gradleProperty("ndkVersion").orElse("27.2.12479018").get()
    defaultConfig {
        minSdk = 23
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared"; cppFlags += "-O3" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = providers.gradleProperty("cmakeVersion").orElse("3.22.1").get() } }
}
