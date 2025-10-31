import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")

    alias(libs.plugins.serialization)
    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":exposed-migration-core"))
    api(project(":exposed-r2dbc"))

    testImplementation(project(":exposed-r2dbc-tests"))
    testImplementation(project(":exposed-json"))
    testImplementation(project(":exposed-kotlin-datetime"))
    testImplementation(project(":exposed-java-time"))
    testImplementation(project(":exposed-money"))

    testRuntimeOnly(libs.r2dbc.h2)
    testRuntimeOnly(libs.r2dbc.mariadb)
    testRuntimeOnly(libs.r2dbc.mysql)
    testRuntimeOnly(libs.r2dbc.oracle)
    testRuntimeOnly(libs.r2dbc.postgresql)
    testRuntimeOnly(libs.r2dbc.sqlserver)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "11"
}
