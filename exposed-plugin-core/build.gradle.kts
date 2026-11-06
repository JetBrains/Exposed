import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")

    alias(libs.plugins.dokka)
}

group = "org.jetbrains.exposed.plugin"
version = "1.6.1"
description = "Exposed Plugin Core"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-migration-jdbc"))

    implementation(libs.kotlin.stdlib)

    implementation(libs.kotlinpoet)

    implementation(libs.flyway.postgresql)
    implementation(libs.flyway.mysql)
    implementation(libs.flyway.sqlserver)
    implementation(libs.flyway.oracle)

    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.mysql)
    implementation(libs.testcontainers.mariadb)
    implementation(libs.testcontainers.mssqlserver)
    implementation(libs.testcontainers.oracle)

    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.h2)
    testImplementation(project(":exposed-kotlin-datetime"))
    testImplementation(project(":exposed-java-time"))
    testImplementation(project(":exposed-json"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "11"
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.VERSION_1_8 > JavaVersion.current()) {
        jvmArgs("-XX:MaxPermSize=256m")
    }

    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }

    useJUnitPlatform()
}
