plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "vn.htq.mobile"
    compileSdk = 35
    buildToolsVersion = "34.0.0"
    defaultConfig {
        applicationId = "vn.htq.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.6.1-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += listOf("ogg", "webp", "png", "ttf", "otf") }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/htq-font/assets"))
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
// Publication is deliberately disabled until the source-rights, font and device gates close.
androidComponents.beforeVariants(androidComponents.selector().withBuildType("release")) { it.enable = false }
