plugins {
    kotlin("jvm")
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
}

group = "org.jetbrains.exposed.samples"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(project(":exposed-core"))
    implementation(project(":exposed-r2dbc"))
    implementation(project(":exposed-dao-r2dbc"))
    implementation(project(":exposed-kotlin-datetime"))

    implementation(libs.r2dbc.h2)

    implementation(libs.logback.classic)
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}
