plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localDotEnv: Map<String, String> = rootProject.file(".env")
    .takeIf { it.isFile }
    ?.readLines()
    ?.mapNotNull { line ->
        val value = line.trim()
        if (value.isEmpty() || value.startsWith("#") || !value.contains('=')) {
            null
        } else {
            val key = value.substringBefore('=').trim()
            val parsedValue = value.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
            key to parsedValue
        }
    }
    ?.toMap()
    ?: emptyMap()

fun signingValue(name: String): String? = providers.environmentVariable(name).orNull ?: localDotEnv[name]

android {
    namespace = "com.sunrecipes.app"
    compileSdk = 35

    val releaseKeystore = signingValue("SUN_RECIPES_KEYSTORE")
    val releaseKeyAlias = signingValue("SUN_RECIPES_KEY_ALIAS")
    val releaseStorePassword = signingValue("SUN_RECIPES_STORE_PASSWORD")
    val releaseKeyPassword = signingValue("SUN_RECIPES_KEY_PASSWORD")

    signingConfigs {
        create("release") {
            if (releaseKeystore != null && releaseKeyAlias != null && releaseStorePassword != null && releaseKeyPassword != null) {
                storeFile = rootProject.file(releaseKeystore)
                keyAlias = releaseKeyAlias
                storePassword = releaseStorePassword
                this.keyPassword = releaseKeyPassword
            } else {
                // Keeps debug builds usable; release fails until the signing environment is configured.
                storeFile = file("$rootDir/.missing-release-keystore.jks")
            }
        }
    }

    defaultConfig {
        applicationId = "com.sunrecipes.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    applicationVariants.all {
        if (name == "release") {
            outputs.all {
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "sunsrecipes.apk"
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
