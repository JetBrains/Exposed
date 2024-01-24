plugins {
    kotlin("jvm") apply true
    alias(libs.plugins.serialization) apply true
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    api(project(":exposed-core"))
    api(libs.joda.time)
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation(project(":exposed-json"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
