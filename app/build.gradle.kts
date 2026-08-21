// Google's published sample AdMob App ID. Serves test ads only, and is the documented
// value for development. Shipping it to real users would serve them test ads and breach the
// AdMob policy, so the release build refuses it — see checkReleaseAdmobAppId below.
val sampleAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val realAdmobAppId = providers.gradleProperty("photosweep.admobAppId")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.simobr.photosweep"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.simobr.photosweep"
        // minSdk 30 is deliberate: MediaStore.createTrashRequest() and createDeleteRequest()
        // arrived in API 30. Anything lower would need a second, legacy,
        // WRITE_EXTERNAL_STORAGE destructive path with no system trash. We do not ship that.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = sampleAdmobAppId
        }
        release {
            manifestPlaceholders["admobAppId"] = realAdmobAppId.getOrElse("MISSING_ADMOB_APP_ID")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Room's exported schemas must be readable from the instrumented test APK, so
    // MigrationTestHelper can build a v1 database and migrate it for real.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }

    buildFeatures {
        compose = true
        // GallerySeeder is gated on BuildConfig.DEBUG.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * Room 2.8.4 ships a serialization BOM pinning kotlinx-serialization-core to 1.7.3, but its
 * own schema-bundle serializers were generated against a newer contract, where
 * `GeneratedSerializer.typeParametersSerializers()` has a default implementation and is no
 * longer overridden. Run those classes on 1.7.3 and MigrationTestHelper dies with
 * `AbstractMethodError` before it reads a single schema.
 *
 * 1.9.0 is not enough — the default arrives later — so this pins 1.11.0, verified by
 * DatabaseMigrationTest going from AbstractMethodError to green.
 *
 * This raises the version for the instrumented test classpath only. Nothing is added to the
 * app: it is a constraint on a dependency Room already brings, and the shipped APK is
 * untouched.
 */
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // room-testing is an *instrumented* testing library: MigrationTestHelper needs a real
    // SQLite and a real Context. On the JVM classpath it is unusable, so it belongs here.
    androidTestImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * A release build with the sample AdMob App ID would serve test ads to real users and breach
 * the AdMob policy. A release build with no App ID at all crashes on launch. Neither is
 * something to discover from a crash report, so the release build fails here instead.
 *
 * Set it in `local.properties` or `~/.gradle/gradle.properties`:
 *     photosweep.admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
 */
val hasRealAdmobAppId = realAdmobAppId.isPresent
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(hasRealAdmobAppId) {
            "Missing AdMob App ID for the release build. Set photosweep.admobAppId in " +
                "local.properties or ~/.gradle/gradle.properties. See RELEASE.md."
        }
    }
}
