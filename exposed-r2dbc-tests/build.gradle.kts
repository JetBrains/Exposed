import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") apply true
}

kotlin {
    jvmToolchain(11)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.kotlinx.coroutines.debug)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.r2dbc.spi)

    implementation(kotlin("test-junit"))
    implementation(libs.junit)

    implementation(project(":exposed-core"))
    implementation(project(":exposed-r2dbc"))
    implementation(project(":exposed-kotlin-datetime"))

    implementation(libs.slf4j)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    testRuntimeOnly(libs.r2dbc.h2)
    testRuntimeOnly(libs.r2dbc.mariadb)
    testRuntimeOnly(libs.r2dbc.mysql)
    testRuntimeOnly(libs.r2dbc.oracle)
    testRuntimeOnly(libs.r2dbc.postgresql)
    testRuntimeOnly(libs.r2dbc.sqlserver)

    testImplementation(libs.logcaptor)
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
