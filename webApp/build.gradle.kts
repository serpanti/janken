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
            implementation(project(":data"))
            implementation(compose.ui)
            implementation(compose.foundation)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(npm("@mediapipe/tasks-vision", "1.2.2"))
                implementation(npm("@sqlite.org/sqlite-wasm", "3.54.1-build1"))
            }
        }
    }
}

val prepareWebResources by tasks.registering(Sync::class) {
    dependsOn(":kotlinWasmNpmInstall")
    into(layout.buildDirectory.dir("generated/web-resources"))
    from(rootProject.layout.buildDirectory.dir("wasm/node_modules/@mediapipe/tasks-vision")) {
        include("vision_bundle.js", "vision_bundle.mjs", "wasm/**")
        into("mediapipe")
    }
    from(rootProject.file("models/gesture_recognizer.task")) { into("mediapipe") }
    from(rootProject.layout.buildDirectory.dir("wasm/node_modules/@sqlite.org/sqlite-wasm/dist")) {
        include("index.mjs", "sqlite3.wasm", "sqlite3-opfs-async-proxy.js")
        into("sqlite")
    }
    from(rootProject.files("LICENSE", "NOTICE.md"))
    from(rootProject.file("licenses")) { into("licenses") }
}
kotlin.sourceSets.named("wasmJsMain") {
    resources.srcDir(prepareWebResources)
}
