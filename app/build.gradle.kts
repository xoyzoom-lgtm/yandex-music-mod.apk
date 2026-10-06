plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Версия задаётся из CI (тег vX.Y.Z), локально используется dev-версия.
val appVersionName: String = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
val appVersionCode: Int = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

// Подпись релиза: если секреты не заданы, релиз подписывается debug-ключом,
// чтобы APK всё равно можно было установить.
val releaseKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

// Discord Social SDK не распространяется в репозитории (лицензия Discord). Если положить
// discord_partner_sdk.aar в app/libs (или скачать его в CI), собирается версия с Rich Presence,
// иначе — с заглушкой. Application ID: переменная DISCORD_APPLICATION_ID или
// discordApplicationId в gradle.properties / ~/.gradle/gradle.properties.
val discordSdkAar = file("libs/discord_partner_sdk.aar")
val discordSdkBundled = discordSdkAar.exists()
val discordApplicationId: Long = (
    System.getenv("DISCORD_APPLICATION_ID")
        ?: providers.gradleProperty("discordApplicationId").orNull
    )?.trim()?.toLongOrNull() ?: 0L

android {
    namespace = "io.github.xoyzoom.ymmod"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.xoyzoom.ymmod"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("long", "DISCORD_APPLICATION_ID", "${discordApplicationId}L")
        buildConfigField("boolean", "DISCORD_SDK_BUNDLED", "$discordSdkBundled")

        if (discordSdkBundled) {
            externalNativeBuild {
                cmake {
                    // Prefab-пакеты SDK собраны с общей C++ STL.
                    arguments += "-DANDROID_STL=c++_shared"
                }
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(if (discordSdkBundled) "src/presenceDiscord/java" else "src/presenceStub/java")
        }
    }

    if (discordSdkBundled) {
        externalNativeBuild {
            cmake {
                path = file("src/presenceDiscord/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = discordSdkBundled
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media)
    implementation(libs.kotlinx.coroutines.android)

    if (discordSdkBundled) {
        implementation(files(discordSdkAar))
    }

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // В локальных unit-тестах org.json из android.jar — заглушка, подключаем настоящую реализацию.
    testImplementation(libs.org.json)
}
