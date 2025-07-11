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
    jvmToolchain(11)
}

dependencies {
    api(project(":exposed-core"))

    api(libs.r2dbc.spi)
    api(libs.kotlinx.coroutines.reactive)

    implementation(libs.slf4j)

    compileOnly(libs.postgre)
    compileOnly(libs.r2dbc.postgresql)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
