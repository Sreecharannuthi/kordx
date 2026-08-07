plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover) // per-subproject kover for merged coverage
}

android {
    namespace = "com.android.rockages.kordx"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.android.rockages.kordx"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()

        versionCode = 1
        versionName = "0.0.1"

        vectorDrawables {
            useSupportLibrary = true
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
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = false
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
        freeCompilerArgs += "-java-parameters" // required for i18n loader reflection
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        // The 26f test [Media3ItemFactoryTest.nonPlayableSetsBothBrowsableAndPlayableFalse26f]
        // calls `Extras.toBundle()` to verify the placeholder shape; on
        // the JVM, `android.os.BaseBundle.putString` is unmocked and
        // throws "Method ... not mocked". Returning defaults makes
        // unmocked framework methods no-ops so the test runs cleanly
        // (the test only inspects `item.mediaId` + the
        // `MediaMetadata` flags, never the bundle contents).
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

// Exclude release test variant from kover — 3 tests depend on DEBUG BuildConfig.
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("testReleaseUnitTest")
        }
    }
}

dependencies {
    implementation(project(":metaphony"))
    implementation(project(":core"))
    implementation(project(":infra"))
    implementation(libs.activity.compose)
    implementation(libs.coil)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.core)
    implementation(libs.core.splashscreen)
    implementation(libs.fuzzywuzzy)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lifecycle.runtime)
    implementation(libs.media)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.okhttp3)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.compose.ui.test.junit4)
}