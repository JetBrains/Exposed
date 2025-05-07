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

    // TODO are both needed? can we remove 1?
    compileOnly(libs.postgre)
    compileOnly(libs.r2dbc.postgresql)
}
// TODO confirm use of repomix.config.json +/- remove?

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
