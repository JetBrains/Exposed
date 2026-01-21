import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    // Configure source sets for KSP
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

dependencies {
    implementation(project(":exposed-interface-dao"))
    implementation(project(":exposed-core"))
    implementation(project(":exposed-dao"))
    implementation(project(":exposed-jdbc"))

    // KSP processor
    ksp(project(":exposed-interface-dao-ksp"))

    // Test dependencies
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(kotlin("test"))
    testImplementation(libs.h2)
    testImplementation(libs.slf4j)
    testImplementation(libs.log4j.slf4j.impl)
    testImplementation(libs.log4j.api)
    testImplementation(libs.log4j.core)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
