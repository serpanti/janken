import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("janken")
        browser { commonWebpackConfig { outputFileName = "janken.js" } }
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":camera"))
            implementation(compose.ui)
            implementation(compose.foundation)
        }
        val wasmJsMain by getting {
            dependencies { implementation(npm("@mediapipe/tasks-vision", "1.2.2")) }
        }
    }
}

val prepareCameraResources by tasks.registering(Copy::class) {
    dependsOn(":kotlinWasmNpmInstall")
    into(layout.buildDirectory.dir("generated/camera-resources"))
    from(rootProject.layout.buildDirectory.dir("wasm/node_modules/@mediapipe/tasks-vision")) {
        include("vision_bundle.js", "vision_bundle.mjs", "wasm/**")
        into("mediapipe")
    }
    from(rootProject.file("models/gesture_recognizer.task")) { into("mediapipe") }
    from(rootProject.files("LICENSE", "NOTICE.md"))
    from(rootProject.file("licenses")) { into("licenses") }
}
kotlin.sourceSets.named("wasmJsMain") {
    resources.srcDir(layout.buildDirectory.dir("generated/camera-resources"))
}
tasks.matching { it.name == "wasmJsProcessResources" }.configureEach { dependsOn(prepareCameraResources) }
