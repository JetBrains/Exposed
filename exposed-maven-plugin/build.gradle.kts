import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")

    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.plugin.development)
}

group = "org.jetbrains.exposed.plugin"
version = "1.3.0"
description = "Exposed Maven Plugin"

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
    implementation(project(":exposed-migration-plugin-core"))

    implementation(libs.kotlin.stdlib)

    implementation(libs.maven.api)
    implementation(libs.maven.core)
    implementation(libs.maven.annotations)

    implementation(libs.flyway.postgresql)
    implementation(libs.flyway.mysql)
    implementation(libs.flyway.sqlserver)
    implementation(libs.flyway.oracle)

    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.mysql)
    implementation(libs.testcontainers.mariadb)
    implementation(libs.testcontainers.mssqlserver)
    implementation(libs.testcontainers.oracle)

    implementation(libs.h2)
    implementation(libs.mysql)
    implementation(libs.postgre)
    implementation(libs.mariadb)
    implementation(libs.oracle)
    implementation(libs.mssql)

    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(kotlin("test-junit5"))
}

mavenPlugin {
    helpMojoPackage = "org.jetbrains.exposed.v1.maven.plugin"
}

signing {
    isRequired = gradle.taskGraph.hasTask("publish")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
    if (name == "compileKotlin") {
        destinationDirectory.set(layout.buildDirectory.dir("classes/java/main"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "11"
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
