plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "fr.nrobeveille.tvlauncher"
    compileSdk = 35

    // Numéro de version : numéro du build GitHub Actions (1 pour une compilation locale).
    val buildNumber = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "fr.nrobeveille.tvlauncher"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        // Dépôt GitHub où chercher les mises à jour (releases).
        buildConfigField("String", "UPDATE_REPO", "\"nrobeveille/tv-launcher\"")
        // Jeton optionnel, seulement utile si le dépôt est privé.
        buildConfigField("String", "UPDATE_TOKEN", "\"${System.getenv("UPDATE_TOKEN").orEmpty()}\"")
    }

    // Clé de signature stable, fournie par les secrets GitHub Actions.
    // Sans elle (compilation locale), on signe avec la clé de debug.
    val releaseStoreFile = System.getenv("SIGNING_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "tvlauncher"
                keyPassword = System.getenv("SIGNING_PASSWORD")
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
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.datastore.preferences)
}
