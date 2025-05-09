plugins {
    kotlin("jvm")
    alias(libs.plugins.serialization)

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
    api(libs.kotlinx.serialization)
    compileOnly(libs.postgre)
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation(project(":exposed-jdbc"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
