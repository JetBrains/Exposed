import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
    kotlin("plugin.serialization") apply true
    id("testWithDBs")
    id("org.jetbrains.exposed.gradle.conventions.dokka")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
    api("org.jetbrains.kotlinx", "kotlinx-serialization-json", Versions.kotlinxSerialization)
    compileOnly("org.postgresql", "postgresql", Versions.postgre)
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))
}
