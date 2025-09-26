plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20" // Add from Mazer if needed for JSON
}

android {
    namespace = "com.jmisabella.zrooms"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jmisabella.zrooms"
        minSdk = 21 // Or raise to 28 like Mazer for simplicity
        targetSdk = 36
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
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.preference:preference:1.2.1") // Remove if not used, as Mazer doesn't have it

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.9.2") // Align with Mazer
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Add from Mazer if needed
}

//
//plugins {
//    alias(libs.plugins.android.application)
//    alias(libs.plugins.kotlin.android)
//    alias(libs.plugins.kotlin.compose)
//}
//
//android {
//    namespace = "com.jmisabella.zrooms"
//    compileSdk = 36
//
//    defaultConfig {
//        applicationId = "com.jmisabella.zrooms"
//        minSdk = 21
//        targetSdk = 36
//        versionCode = 1
//        versionName = "1.0"
//
//        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//    }
//
//    buildTypes {
//        release {
//            isMinifyEnabled = false
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
//        }
//    }
//    buildFeatures {
//        compose = true
//    }
//    composeOptions {
//        // Bumped minimally to 1.5.14 for better 1.6.0 compatibility and blendMode support
//        // (matches Kotlin 1.8.x/1.9.x; avoids 1.7.0 mismatch)
//        kotlinCompilerExtensionVersion = "1.5.14"
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_11
//        targetCompatibility = JavaVersion.VERSION_11
//    }
//    kotlinOptions {
//        jvmTarget = "11"
//    }
//    buildFeatures {
//        compose = true
//    }
//}
//
//dependencies {
//    implementation("androidx.core:core-ktx:1.13.1")
//    implementation("androidx.appcompat:appcompat:1.7.0")
//    implementation("com.google.android.material:material:1.12.0")
//    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
//    implementation("androidx.media:media:1.7.0")
//    implementation("androidx.preference:preference:1.2.1") // Added for PreferenceManager
//
//    // Use Compose BOM for version consistency (keep as-is; it's working)
//    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
//    implementation(composeBom)
//    androidTestImplementation(composeBom)
//
//    // BOM-managed Compose deps (no explicit versions; remove duplicates to avoid conflicts)
//    implementation("androidx.compose.ui:ui")
//    implementation("androidx.compose.ui:ui-graphics")
//    implementation("androidx.compose.foundation:foundation")
//    implementation("androidx.compose.material:material")
//    implementation("androidx.compose.material:material-icons-extended")
//    implementation("androidx.compose.ui:ui-tooling-preview")
//    debugImplementation("androidx.compose.ui:ui-tooling")
//    implementation("androidx.compose.material3:material3")
//
//    // Non-BOM deps: Explicit versions compatible with Compose 1.6.0/BOM 2024.09.00
//    // (BOM doesn't cover these; pin to stable releases to fix resolution errors)
//    implementation("androidx.activity:activity-compose:1.9.3") // Latest stable as of Sep 2025
//    implementation("androidx.navigation:navigation-compose:2.8.3") // Latest stable (2.8.3; avoids alpha 2.9.x issues)
//    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7") // Latest stable
//    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
//
//    // Testing dependencies (align versions with BOM where possible)
//    testImplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test.ext:junit:1.2.1")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
//    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
//    debugImplementation("androidx.compose.ui:ui-test-manifest")
//}