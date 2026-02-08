plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.music.wallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.music.wallpaper"
        minSdk = 26  // Updated for modern notification APIs
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // Color extraction from album art
    implementation("androidx.palette:palette:1.0.0")
    
    // Settings screen
    implementation("androidx.preference:preference:1.2.1")
    
    // Onboarding ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // LocalBroadcastManager for service communication
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}