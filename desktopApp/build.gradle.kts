import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    implementation(project(":shared"))
    implementation(project(":camera"))
    implementation(project(":data"))
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}
val bundleCameraRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Bundle Python, OpenCV and MediaPipe for the current OS (developer build only)"
    val windows = System.getProperty("os.name").startsWith("Windows")
    val localPython = rootProject.file(if (windows) ".camera-venv/Scripts/python.exe" else ".camera-venv/bin/python")
    val python = providers.environmentVariable("JANKEN_BUILD_PYTHON").orNull
        ?: localPython.takeIf { it.isFile }?.absolutePath
        ?: if (windows) "python" else "python3"
    commandLine(python, rootProject.file("desktop-camera/build_worker.py").absolutePath)
    workingDir(rootProject.projectDir)
    inputs.files(rootProject.files("desktop-camera/build_worker.py", "desktop-camera/camera.spec",
        "desktop-camera/camera_worker.py", "desktop-camera/requirements.txt", "desktop-camera/requirements-build.txt",
        "models/gesture_recognizer.task"))
    outputs.dir(layout.buildDirectory.dir("camera-resources"))
}

compose.desktop {
    application {
        mainClass = "dev.janken.desktop.MainKt"
        providers.gradleProperty("janken.packagingJavaHome").orNull?.let { javaHome = it }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Janken"
            packageVersion = "0.6.0"
            modules("java.desktop", "java.logging", "java.management", "jdk.unsupported", "jdk.crypto.ec")
            appResourcesRootDir.set(layout.buildDirectory.dir("camera-resources"))
            macOS {
                // jpackage requires a positive major version for macOS bundles.
                packageVersion = "1.0.0"
                bundleID = "dev.janken.prototype"
                infoPlist { extraKeysRawXml = "<key>NSCameraUsageDescription</key><string>Камера нужна для игры жестами. Изображение обрабатывается локально.</string>" }
            }
            linux { packageName = "janken" }
        }
    }
}

// prepareAppResources copies the generated runtime into app/resources.
tasks.matching { it.name == "prepareAppResources" || it.name == "createDistributable" || it.name == "createReleaseDistributable" }
    .configureEach { dependsOn(bundleCameraRuntime) }

tasks.processResources {
    from(rootProject.file("desktop-camera")) {
        into("desktop-camera")
        exclude("__pycache__/**", "test_*.py")
    }
    from(rootProject.file("models/gesture_recognizer.task")) { into("desktop-camera") }
}

tasks.test {
    val work = rootProject.layout.projectDirectory.dir(".work").asFile
    doFirst { work.resolve("jvm-temp").mkdirs(); work.resolve("skiko-test").mkdirs() }
    systemProperty("java.io.tmpdir", work.resolve("jvm-temp").absolutePath)
    systemProperty("skiko.data.path", work.resolve("skiko-test").absolutePath)
    environment("JANKEN_CAMERA_TEST_IMAGE", rootProject.file("app/src/androidTest/assets/fist.jpg").absolutePath)
    val localTestPython = rootProject.file(if (System.getProperty("os.name").startsWith("Windows"))
        ".camera-venv/Scripts/python.exe" else ".camera-venv/bin/python")
    val testPython = providers.environmentVariable("JANKEN_TEST_PYTHON").orNull
        ?: localTestPython.takeIf { it.isFile }?.absolutePath
    testPython?.let { environment("JANKEN_TEST_PYTHON", it); environment("JANKEN_PYTHON", it) }
    providers.gradleProperty("janken.testCameraResources").orNull?.let {
        systemProperty("compose.application.resources.dir", it)
        // The packaged branch must work even with an unusable external Python.
        environment("JANKEN_PYTHON", rootProject.file(".work/no-external-python").absolutePath)
    }
}
