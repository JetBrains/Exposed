import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
    kotlin("plugin.serialization") apply true
    id("testWithDBs")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
    api("joda-time", "joda-time", "2.10.13")
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", Versions.kotlinxSerialization)
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))
}
