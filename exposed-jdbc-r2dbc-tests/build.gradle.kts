import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") apply true

    // exposed-json dependencies
    alias(libs.plugins.serialization)
}

kotlin {
    // TODO REQUIRED for exposed-crypt tests, but makes Oracle tests fail...
    //  https://youtrack.jetbrains.com/issue/EXPOSED-905/Bump-com.oracle.database.jdbc-to-ojdbc11
    // jvmToolchain(17)
    jvmToolchain(11)

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
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-tests"))
    implementation(project(":exposed-r2dbc"))
    implementation(project(":exposed-r2dbc-tests"))

    testRuntimeOnly(libs.r2dbc.pool)
    testImplementation(libs.r2dbc.h2) {
        exclude(group = "com.h2database", module = "h2")
    }
    testRuntimeOnly(libs.r2dbc.mariadb)
    testRuntimeOnly(libs.r2dbc.mysql)
    testRuntimeOnly(libs.r2dbc.oracle)
    testImplementation(libs.r2dbc.postgresql)
    testRuntimeOnly(libs.r2dbc.sqlserver)

    implementation(libs.slf4j)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    testImplementation(libs.logcaptor)
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
