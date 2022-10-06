plugins {
    kotlin("jvm") version "1.7.20-RC"
}

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":tikrana-commons"))
    implementation("io.arrow-kt:arrow-core:1.1.3-rc.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.20")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
