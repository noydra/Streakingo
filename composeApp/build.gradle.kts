import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val appVersion: String = (findProperty("appVersion") as String?)?.trim()?.removePrefix("v") ?: "1.0.0"
require(Regex("""[1-9]\d*\.\d+\.\d+""").matches(appVersion)) {
    "appVersion must look like 1.2.3, got '$appVersion'"
}
val appVersionCode: Int = appVersion.split(".").map(String::toInt).let { (major, minor, patch) ->
    major * 10_000 + minor * 100 + patch
}

val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(property)

val generateAppVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/appVersion")
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/noydra/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.noydra\n\nconst val APP_VERSION = \"$version\"\n")
    }
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain {
            kotlin.srcDir(generateAppVersion)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.sqlite.bundled)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.play.services.code.scanner)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.websockets)
            implementation(libs.zxing.core)
        }
    }
}

android {
    namespace = "com.noydra"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.noydra"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }
    signingConfigs {
        create("release") {
            signingValue("ANDROID_KEYSTORE_PATH", "storeFile")?.let { path ->
                storeFile = rootProject.file(path)
                storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
            }
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
}

compose.resources {
    packageOfResClass = "com.noydra.resources"
}

compose.desktop {
    application {
        mainClass = "com.noydra.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Streakingo"
            packageVersion = appVersion
            vendor = "noydra"
            description = "Duolingo streak saver"
            modules("jdk.unsupported")
            macOS {
                bundleID = "com.noydra"
                iconFile.set(project.file("icons/Streakingo.icns"))
            }
            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6f1c2a7e-4f0b-4b8e-9a51-3c2d7e8f9a10"
                iconFile.set(project.file("icons/Streakingo.ico"))
            }
            linux {
                iconFile.set(project.file("icons/Streakingo.png"))
            }
        }
    }
}
