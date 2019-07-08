import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.setupDialectTest

plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
}

repositories {
    mavenCentral()
}

val dialect: String by project

dependencies {
//    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.0-M1")
    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.0-M1")
    testImplementation(project(":exposed-jdbc"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.slf4j", "slf4j-log4j12", "1.7.26")
    testImplementation("log4j", "log4j", "1.2.17")
    testImplementation("junit", "junit", "4.12")
    testImplementation("org.hamcrest", "hamcrest-library", "1.3")
    testImplementation("org.jetbrains.kotlinx","kotlinx-coroutines-debug", "1.3.0-M1")

    testImplementation("com.opentable.components", "otj-pg-embedded", "0.12.0")
    testImplementation("mysql", "mysql-connector-mxj", "5.0.12")
    testImplementation("org.xerial", "sqlite-jdbc", "3.23.1")
//    implementation("com.h2database", "h2", "1.4.199")
    testImplementation("com.h2database", "h2", "1.4.199")

    when (dialect) {
        "mariadb" ->    testImplementation("org.mariadb.jdbc", "mariadb-java-client", "2.4.1")
        "mysql" ->      testImplementation("mysql", "mysql-connector-java", "8.0.16")
        "oracle" ->     testImplementation("com.oracle", "ojdbc6", "12.1.0.1-atlassian-hosted")
        "sqlserver" ->  testImplementation("com.microsoft.sqlserver", "mssql-jdbc", "7.2.2.jre8")
        else -> {
            testImplementation("com.h2database", "h2", "1.4.199")
            testImplementation("mysql", "mysql-connector-java", "5.1.47")
            testImplementation("org.postgresql", "postgresql", "42.2.5.jre6")
        }
    }

}

tasks.withType(Test::class.java) {
    jvmArgs = listOf("-XX:MaxPermSize=256m")
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

setupDialectTest(dialect)