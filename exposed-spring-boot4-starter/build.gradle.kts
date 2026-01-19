import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
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
    api(project(":exposed-dao"))
    api(project(":spring7-transaction"))
    api(libs.spring.boot4.starter.jdbc)
    api(libs.spring.boot4.autoconfigure)
    compileOnly(libs.spring.boot4.configuration.processor)

    testImplementation(libs.spring.boot4.starter.test)
    testImplementation(libs.spring.boot4.starter.webflux)
    testImplementation(libs.h2)
    testImplementation(project(":exposed-jdbc"))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit6)
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
    useJUnitPlatform()
}
