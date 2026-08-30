plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.captord.screenrecorder"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.captord.screenrecorder"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Resolve the keystore path and credentials from environment
            // variables only. Never hardcode paths in the build script.
            val keystorePath = System.getenv("KEYSTORE_PATH")
                ?: (project.findProperty("KEYSTORE_PATH") as String?)
                ?: ""
            if (keystorePath.isNotBlank()) {
                storeFile = file(keystorePath)
            }
            val storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: (project.findProperty("KEYSTORE_PASSWORD") as String?)
                ?: ""
            val keyAlias = System.getenv("KEY_ALIAS")
                ?: (project.findProperty("KEY_ALIAS") as String?)
                ?: ""
            val keyPassword = System.getenv("KEY_PASSWORD")
                ?: (project.findProperty("KEY_PASSWORD") as String?)
                ?: ""
            this.storePassword = storePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
    }

    afterEvaluate {
        // Fail loud at release-task execution time when env vars are
        // missing. We can't `require()` at config time because Gradle
        // also evaluates this block when building debug variants.
        tasks.matching {
            it.name == "assembleRelease" || it.name == "bundleRelease"
        }.configureEach {
            doFirst {
                val keystorePath = System.getenv("KEYSTORE_PATH")
                    ?: (project.findProperty("KEYSTORE_PATH") as String?)
                    ?: ""
                if (keystorePath.isBlank()) {
                    throw org.gradle.api.GradleException(
                        "KEYSTORE_PATH must be set (env var or gradle property) for release builds",
                    )
                }
                val missing = listOf(
                    "KEYSTORE_PASSWORD" to (System.getenv("KEYSTORE_PASSWORD")
                        ?: (project.findProperty("KEYSTORE_PASSWORD") as String?)
                        ?: ""),
                    "KEY_ALIAS" to (System.getenv("KEY_ALIAS")
                        ?: (project.findProperty("KEY_ALIAS") as String?)
                        ?: ""),
                    "KEY_PASSWORD" to (System.getenv("KEY_PASSWORD")
                        ?: (project.findProperty("KEY_PASSWORD") as String?)
                        ?: ""),
                ).filter { it.second.isBlank() }
                if (missing.isNotEmpty()) {
                    throw org.gradle.api.GradleException(
                        "Missing release signing env vars: ${missing.joinToString { it.first }}",
                    )
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            merges += "META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
