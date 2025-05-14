import org.gradle.api.tasks.testing.logging.*

plugins {
    kotlin("jvm") apply true
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(8)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.debug)

    implementation(kotlin("test-junit"))
    implementation(libs.junit)

    implementation(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-dao"))
    implementation(project(":exposed-json"))
    implementation(project(":exposed-kotlin-datetime"))
    implementation(project(":exposed-money"))
    implementation(project(":exposed-migration"))

    kover(project(":exposed-core"))
    kover(project(":exposed-jdbc"))
    kover(project(":exposed-dao"))
    kover(project(":exposed-json"))
    kover(project(":exposed-kotlin-datetime"))
    kover(project(":exposed-money"))
    kover(project(":exposed-migration"))

    implementation(libs.slf4j)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    implementation(libs.hikariCP)
    testCompileOnly(libs.mysql)
    testCompileOnly(libs.postgre)
    testCompileOnly(libs.pgjdbc.ng)
    testCompileOnly(libs.mssql)
    compileOnly(libs.h2)
    testCompileOnly(libs.sqlite.jdbc)
    testImplementation(libs.logcaptor)
    testImplementation(libs.kotlinx.coroutines.test)
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
