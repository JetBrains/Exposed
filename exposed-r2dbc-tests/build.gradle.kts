import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType

plugins {
    kotlin("jvm") apply true

    // exposed-json dependencies
    alias(libs.plugins.serialization)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
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

    // non-exposed-tests module dependencies
    // --- start ---
    testImplementation(project(":exposed-money"))
    testImplementation(project(":exposed-crypt"))
    testImplementation(project(":exposed-json"))
    testImplementation(project(":exposed-java-time"))
    testImplementation(project(":exposed-jodatime"))
    // --- end ----

    testRuntimeOnly(libs.r2dbc.pool)
    testImplementation(libs.r2dbc.h2) {
        exclude(group = "com.h2database", module = "h2")
    }
    testRuntimeOnly(libs.r2dbc.mariadb)
    testRuntimeOnly(libs.r2dbc.mysql)
    testRuntimeOnly(libs.r2dbc.oracle)
    testImplementation(libs.r2dbc.postgresql)
    testRuntimeOnly(libs.r2dbc.sqlserver)

    testImplementation(libs.logcaptor)

    // exposed-money dependencies
    testImplementation(libs.moneta)
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.VERSION_11 > JavaVersion.current()) {
        jvmArgs = listOf("-XX:MaxPermSize=256m")
    }
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}
