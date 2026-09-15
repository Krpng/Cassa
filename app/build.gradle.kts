plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "it.krpng.cassa"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.krpng.cassa"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Room MigrationTestHelper (room-migration 2.8.4) deserializes schema JSON with
// kotlinx-serialization serializers compiled against 1.8.x APIs.
// lifecycle-viewmodel-savedstate 2.10.0 transitively requests 1.7.3.
// Instrumented tests share one process between the debug app APK and the
// androidTest APK; forcing only androidTest left debug on 1.7.3 and broke
// Compose UI tests (no compose hierarchy / classloader clash).
// Scope: debug + androidTest classpaths only — not release.
configurations
    .matching { configuration ->
        val name = configuration.name
        name.contains("AndroidTest", ignoreCase = true) ||
            (name.contains("debug", ignoreCase = true) && name.contains("Classpath", ignoreCase = true))
    }
    .configureEach {
        resolutionStrategy {
            force(
                "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
                "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
                "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
                "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
            )
        }
    }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.kxml2)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    // Room 2.8 schema JSON deserialization requires serialization >= 1.8.x;
    // without this, MigrationTestHelper hits AbstractMethodError vs forced 1.7.3.
    androidTestImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.kotlinx.serialization.core)
    add("kspAndroidTest", libs.room.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}
