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
    api(project(":exposed-migration-core"))
    api(project(":exposed-jdbc"))

    testImplementation(project(":exposed-tests"))
    testImplementation(project(":exposed-json"))
    testImplementation(project(":exposed-kotlin-datetime"))
    testImplementation(project(":exposed-java-time"))
    testImplementation(project(":exposed-money"))
    testCompileOnly(libs.postgre)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
