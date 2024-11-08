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
    testImplementation(project(":exposed-jdbc"))
    testImplementation(libs.junit)
//    testImplementation(libs.r2dbc.h2)
//    testImplementation(libs.r2dbc.mariadb)
//    testImplementation(libs.r2dbc.mysql)
//    testImplementation(libs.r2dbc.postgresql)
    testImplementation(kotlin("test-junit"))
}
