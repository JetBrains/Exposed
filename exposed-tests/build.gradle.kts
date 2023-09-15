import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
}

kotlin {
    jvmToolchain(8)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotlinCoroutines)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-debug", Versions.kotlinCoroutines)

    implementation(kotlin("test-junit"))
    implementation("junit", "junit", "4.12")
    implementation("org.hamcrest", "hamcrest-library", "1.3")

    implementation(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-dao"))
    implementation(project(":exposed-crypt"))

    implementation("org.slf4j", "slf4j-api", Versions.slf4j)
    implementation("org.apache.logging.log4j", "log4j-slf4j-impl", Versions.log4j2)
    implementation("org.apache.logging.log4j", "log4j-api", Versions.log4j2)
    implementation("org.apache.logging.log4j", "log4j-core", Versions.log4j2)

    implementation("com.zaxxer", "HikariCP", "4.0.3")
    testCompileOnly("org.postgresql", "postgresql", Versions.postgre)
    testCompileOnly("com.impossibl.pgjdbc-ng", "pgjdbc-ng", Versions.postgreNG)
    compileOnly("com.h2database", "h2", Versions.h2)
    testCompileOnly("org.xerial", "sqlite-jdbc", Versions.sqlLite3)
    testImplementation("io.github.hakky54:logcaptor:2.9.0")
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
