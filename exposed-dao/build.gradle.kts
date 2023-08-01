plugins {
    kotlin("jvm") apply true
    id("org.jetbrains.exposed.gradle.conventions.dokka")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
}
