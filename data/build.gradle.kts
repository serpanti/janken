import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

kotlin {
    android {
        namespace = "dev.janken.data"
        compileSdk = 36
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(libs.room.runtime)
        }
        androidMain.dependencies { implementation(libs.sqlite.bundled) }
        named("desktopMain") { dependencies { implementation(libs.sqlite.bundled) } }
        iosMain.dependencies { implementation(libs.sqlite.bundled) }
        wasmJsMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            implementation(libs.sqlite.web)
        }
        named("desktopTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
    }
}

// With AGP 8.13, KSP generates Room's Android classes but does not add them to compilation.
val androidRoomSources = files(layout.buildDirectory.dir("generated/ksp/android/androidMain/kotlin"))
    .builtBy("kspAndroidMain")
kotlin.sourceSets.androidMain {
    kotlin.srcDir(androidRoomSources)
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspWasmJs", libs.room.compiler)
}

room3 { schemaDirectory("$projectDir/schemas") }
