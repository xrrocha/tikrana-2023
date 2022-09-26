import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20-RC"
    application
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("io.arrow-kt:arrow-core:1.1.3-rc.1")

    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.4")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClass.set("plenix.tikrana.webserver.MainKt")
}
