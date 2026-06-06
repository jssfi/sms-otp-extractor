plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jss.smsotpextractor"
    compileSdk = 36
    val releaseStoreFile = System.getenv("ANDROID_SIGNING_STORE_FILE")
    val releaseStorePassword = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")

    defaultConfig {
        applicationId = "com.jss.smsotpextractor"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    flavorDimensions += "modelDistribution"
    productFlavors {
        create("bundled") {
            dimension = "modelDistribution"
            buildConfigField("boolean", "BUNDLED_MODEL", "true")
        }
        create("lite") {
            dimension = "modelDistribution"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "BUNDLED_MODEL", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (
            !releaseStoreFile.isNullOrBlank() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.0")
}

tasks.register("assembleAllDebug") {
    group = "build"
    description = "Builds both bundled and lite debug APKs."
    dependsOn("assembleBundledDebug", "assembleLiteDebug")
}

tasks.register("assembleAllRelease") {
    group = "build"
    description = "Builds both bundled and lite release APKs."
    dependsOn("assembleBundledRelease", "assembleLiteRelease")
}
