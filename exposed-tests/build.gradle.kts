import org.gradle.api.tasks.testing.logging.*
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") apply true
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
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.debug)

    implementation(kotlin("test-junit5"))
    implementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-dao"))
    implementation(project(":exposed-kotlin-datetime"))

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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "8"
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
