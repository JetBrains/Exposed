import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")

    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":exposed-core"))

    api(libs.r2dbc.spi)
    api(libs.kotlinx.coroutines.reactive)

    implementation(libs.slf4j)

    compileOnly(libs.r2dbc.postgresql)
}
