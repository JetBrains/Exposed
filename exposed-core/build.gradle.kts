import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

dependencies {
    api(kotlin("stdlib"))
    api(kotlin("reflect"))
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotlinCoroutines)
    api("org.slf4j", "slf4j-api", "1.7.25")
}
