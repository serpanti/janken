pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "Janken"
include(":app", ":domain", ":data", ":presentation", ":camera", ":shared", ":desktopApp", ":webApp")
