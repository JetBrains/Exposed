import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType
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
    api(project(":exposed-r2dbc"))
    api(libs.spring.r2dbc)
    api(libs.spring.context)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-r2dbc-tests"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.spring.test)
    testImplementation(libs.slf4j)
    testImplementation(libs.log4j.slf4j.impl)
    testImplementation(libs.log4j.api)
    testImplementation(libs.log4j.core)
    testImplementation(libs.junit)
    testImplementation(libs.r2dbc.h2) {
        exclude(group = "com.h2database", module = "h2")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.VERSION_1_8 > JavaVersion.current()) {
        jvmArgs = listOf("-XX:MaxPermSize=256m")
    }
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}
