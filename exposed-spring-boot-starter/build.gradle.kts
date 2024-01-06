import org.gradle.api.tasks.testing.logging.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm") apply true
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
    api(project(":spring-transaction"))
    api(libs.spring.boot.starter.data.jdbc)
    api(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot.starter.test)
    // put in testImplementation so no hard dependency for those using the starter
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.h2)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
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
