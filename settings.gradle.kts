pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "tikrana"

include(
    "tikrana-commons",
    "tikrana-http-server",
    "tikrana-memory-image",
    "tikrana-memory-acme",
)

