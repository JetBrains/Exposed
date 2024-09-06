plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    api(project(":exposed-core"))

    api(libs.r2dbc.spi)
    api(libs.kotlinx.coroutines.reactive)

    testImplementation(project(":exposed-tests"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
