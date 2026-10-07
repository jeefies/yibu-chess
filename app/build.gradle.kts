plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "cn.yibu.chess"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "28.0.13004108"
    defaultConfig {
        applicationId = "cn.yibu.chess"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("personal") {
            val keyPath = providers.environmentVariable("YIBU_KEYSTORE").orNull
            storeFile = file(keyPath ?: "../signing/personal.jks")
            storePassword = providers.environmentVariable("YIBU_STORE_PASSWORD").orNull ?: "yibu-personal-test"
            keyAlias = "yibu"
            keyPassword = providers.environmentVariable("YIBU_KEY_PASSWORD").orNull ?: "yibu-personal-test"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("personal") }
        release {
            signingConfig = signingConfigs.getByName("personal")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    androidResources { noCompress += "nnue" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
tasks.withType<Test>().configureEach {
    providers.gradleProperty("startupNativeDir").orNull?.let { directory ->
        jvmArgs("-Djava.library.path=$directory", "-Dstartup.native=true")
    }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
