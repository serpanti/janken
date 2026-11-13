import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}
kotlin {
    android {
        namespace = "dev.janken.domain"
        compileSdk = 36
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser(); nodejs() }
    iosArm64()
    iosSimulatorArm64()
    sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}
