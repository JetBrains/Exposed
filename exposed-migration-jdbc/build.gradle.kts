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
    api(project(":exposed-jdbc"))

    testImplementation(project(":exposed-tests"))
    testImplementation(project(":exposed-json"))
    testImplementation(project(":exposed-kotlin-datetime"))
    testImplementation(project(":exposed-java-time"))
    testImplementation(project(":exposed-money"))
    testCompileOnly(libs.postgre)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.logcaptor)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "8"
}
