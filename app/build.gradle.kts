import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.shamashel.workout"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shamashel.workout"
        minSdk = 28
        targetSdk = 36
        versionCode = (findProperty("app.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("app.versionName") as String?) ?: "1.0"
    }

    signingConfigs {
        create("personal") {
            val encoded = System.getenv("RELEASE_KEYSTORE_BASE64").orEmpty().trim()
            val storePasswordEnv = System.getenv("RELEASE_STORE_PASSWORD").orEmpty()
            val aliasEnv = System.getenv("RELEASE_KEY_ALIAS").orEmpty().trim()
            val keyPasswordEnv = System.getenv("RELEASE_KEY_PASSWORD").orEmpty()
            val provided = listOf(encoded, storePasswordEnv, aliasEnv, keyPasswordEnv)
            if (provided.any { it.isNotBlank() } && provided.any { it.isBlank() }) {
                throw org.gradle.api.GradleException(
                    "Set all of RELEASE_KEYSTORE_BASE64, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, " +
                        "and RELEASE_KEY_PASSWORD, or leave all four unset.",
                )
            }
            if (provided.all { it.isNotBlank() }) {
                val decoded = layout.buildDirectory.file("signing/release.keystore").get().asFile
                decoded.parentFile?.mkdirs()
                val compact = encoded.replace(Regex("\\s"), "")
                decoded.writeBytes(Base64.getDecoder().decode(compact))
                storeFile = decoded
                storePassword = storePasswordEnv
                keyAlias = aliasEnv
                keyPassword = keyPasswordEnv
            } else {
                val committed = file("signing/trades-prep-debug.keystore")
                if (!committed.isFile) {
                    throw org.gradle.api.GradleException(
                        "Missing ${committed}. The personal-use keystore is part of the repo. See the README.",
                    )
                }
                storeFile = committed
                storePassword = "trades-prep"
                keyAlias = "trades-prep"
                keyPassword = "trades-prep"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Screenshot tests host a Compose activity that the debug test manifest provides.
// Release unit tests would otherwise look for that activity and fail.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { builder ->
        (builder as HasHostTestsBuilder).hostTests.getValue(HostTestBuilder.UNIT_TEST_TYPE).enable = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":engine"))

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    testImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("androidx.health.connect:connect-client:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
