plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.di)
    implementation(libs.ktor.serialization)

    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.json)

    implementation(libs.postgresql.r2dbc)
    runtimeOnly(libs.postgresql)

    implementation(libs.kotlinx.datetime)

    implementation(libs.logback.classic)
}
