import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.exposed.gradle.Versions


plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
}

val dialect: String by project

dependencies {
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotlinCoroutines)
    implementation(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-dao"))
    implementation(kotlin("test-junit"))
    implementation("org.slf4j", "slf4j-log4j12", "1.7.26")
    implementation("log4j", "log4j", "1.2.17")
    implementation("junit", "junit", "4.12")
    implementation("org.hamcrest", "hamcrest-library", "1.3")
    implementation("org.jetbrains.kotlinx","kotlinx-coroutines-debug", Versions.kotlinCoroutines)

    implementation("com.opentable.components", "otj-pg-embedded", "0.12.0")
    implementation("org.xerial", "sqlite-jdbc", Versions.sqlLite3)
    implementation("com.h2database", "h2", Versions.h2)
    implementation("org.testcontainers", "testcontainers", "1.14.3")
    implementation("org.testcontainers", "mysql", "1.14.3")

    when (dialect) {
        "mariadb" ->    implementation("org.mariadb.jdbc", "mariadb-java-client", Versions.mariaDB)
        "mysql" ->      implementation("mysql", "mysql-connector-java", Versions.mysql51)
        "mysql8" ->     implementation("mysql", "mysql-connector-java", Versions.mysql80)
        "oracle" ->     implementation("com.oracle.database.jdbc", "ojdbc8", Versions.oracle12)
        "sqlserver" ->  implementation("com.microsoft.sqlserver", "mssql-jdbc", Versions.sqlserver)
        else -> {
            implementation("com.h2database", "h2", Versions.h2)
            implementation("mysql", "mysql-connector-java", Versions.mysql51)
            implementation("org.postgresql", "postgresql", Versions.postgre)
            implementation("com.impossibl.pgjdbc-ng", "pgjdbc-ng", Versions.postgreNG)
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