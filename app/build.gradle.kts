import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    kotlin("plugin.serialization") version "1.9.22"
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
}

// Load local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.phad.chatapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phad.chatapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 59
        versionName = "1.1.4"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Configure NDK ABI filters for 16KB page size compatibility
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
        
        multiDexEnabled = true
        
        // Add Cloudinary credentials from local.properties as BuildConfig fields
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${localProperties.getProperty("cloudinary.cloud_name") ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_API_KEY", "\"${localProperties.getProperty("cloudinary.api_key") ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"${localProperties.getProperty("cloudinary.api_secret") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            // Load keystore properties from local.properties
            val keystoreFile = localProperties.getProperty("key.store") ?: "campus-code-0.jks"
            storeFile = file(keystoreFile)
            storePassword = localProperties.getProperty("key.store.password")
            keyAlias = localProperties.getProperty("key.alias")
            keyPassword = localProperties.getProperty("key.alias.password")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // Lint configuration
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        // Enable core library desugaring for java.time APIs
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    // Enable ViewBinding and Compose
    buildFeatures {
        viewBinding = true
        compose = true
        // Enable BuildConfig for debug flags
        buildConfig = true
    }

    // Add packaging options to handle conflicts and 16KB page size alignment
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
        // Configure native library alignment for 16KB page size compatibility
        jniLibs {
            useLegacyPackaging = false
            // Exclude misaligned ML Kit util library until aligned builds are available
            excludes += setOf("**/libimage_processing_util_jni.so")
        }
    }
    
    // Configure NDK for 16KB page size alignment (use default NDK)
    // NDK version will be automatically detected from Android SDK
    
    // Remove forced SoLoader to avoid pulling outdated native libs that are not 16KB-aligned

    // Configure Compose options
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    // Disable Java compilation
    sourceSets {
        getByName("main") {
            java.srcDirs(emptyList<String>())
        }
    }
}

dependencies {
    implementation(libs.androidx.ui.text)
    // Core library desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Core library desugaring for Java 8+ features on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")


    // UI Components
    implementation("de.hdodenhof:circleimageview:3.1.0")
    
    // Image loading and photo viewer
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0") // For pinch-to-zoom image viewing
    
    // ExoPlayer for splash video playback
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    
    // Play Core for update detection
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    

    // Firebase with BOM (Bill of Materials)
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    
    // Declare Firebase dependencies without versions (managed by BOM)
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")

    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-database") {
        // Force a lower version for compatibility
        version {
            strictly("20.2.2")
        }
    }
    
    // Explicitly add reCAPTCHA dependency to ensure security (version 18.4.0+)
    implementation("com.google.android.recaptcha:recaptcha:18.4.0")
    
    // Add Firebase coroutines support
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Google Drive API dependencies
    implementation("com.google.api-client:google-api-client-android:1.33.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.11.0")
    implementation("com.google.guava:guava:31.1-android")
    
    // Cloudinary for file uploads
    implementation("com.cloudinary:cloudinary-android:3.1.2")
    
    // HTTP Client and JSON serialization
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") {
        // Force compatible version with Kotlin 1.9.22
        version {
            strictly("1.6.3")
        }
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3") {
        version {
            strictly("1.6.3")
        }
    }


    // Jetpack Compose dependencies
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.ui:ui-graphics:1.7.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Location services for attendance tracking
    implementation("com.google.android.gms:play-services-location:21.1.0")
    
    // Play Integrity API for app attestation (server-side clone detection)
    implementation("com.google.android.play:integrity:1.3.0")

    // CameraX for face recognition
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // QR Code generation and scanning
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // ML Kit - latest available in repos
    implementation("com.google.mlkit:barcode-scanning:17.3.0")


    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.0.0")
    testImplementation("org.mockito:mockito-inline:5.0.0")
    testImplementation("org.powermock:powermock-api-mockito2:2.0.9")
    testImplementation("org.powermock:powermock-module-junit4:2.0.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Android testing dependencies
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.fragment:fragment-testing:1.6.2")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7") // Use the latest version
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7") // Use the latest version

    // Lifecycle KTX (for lifecycleScope)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0") // Use the latest version

    // ViewModel KTX (for by viewModels())
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Use the latest version

    // Optional: Saved state for ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0") // Use the latest version

    // Optional: Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1") // Use the latest version

    // For Parcelable support with @Parcelize
    // Ensure you have id("kotlin-parcelize") in your plugins block at the top

    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // PDF generation dependencies
    implementation("com.itextpdf:itext7-core:7.2.5")
    implementation("com.itextpdf:html2pdf:4.0.5")

    // Apache POI for Excel generation - using 4.1.2 for better Android compatibility (minSdk 26)
    implementation("org.apache.poi:poi:4.1.2")
    implementation("org.apache.poi:poi-ooxml:4.1.2")
    implementation("com.fasterxml:aalto-xml:1.2.2") // StAX implementation needed for POI on Android
    implementation("javax.xml.stream:stax-api:1.0-2") // StAX API for XMLStreamReader
    
    // Remove explicit SoLoader dependency; not needed and may introduce misaligned native libs
}