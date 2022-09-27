pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "tikrana"

include(
    "tikrana-commons",
    "tikrana-webserver", // TODO Rename tikrana-webserver to tikrana-http-server
    "tikrana-memimg"
)

