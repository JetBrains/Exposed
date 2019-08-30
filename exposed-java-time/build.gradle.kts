import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import tanvd.kosogor.proxy.publishJar

plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
}

val dialect: String by project

dependencies {
    api(project(":exposed-core"))
    testImplementation(project(":exposed-tests"))
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))

    testImplementation("com.opentable.components", "otj-pg-embedded", "0.12.0")
    testImplementation("mysql", "mysql-connector-mxj", "5.0.12")
    testImplementation("org.xerial", "sqlite-jdbc", "3.23.1")
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

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        apiVersion = "1.3"
        languageVersion = "1.3"
    }
}

publishJar {
    publication {
        artifactId = "exposed-java-time"
    }

    bintray {
        username = project.properties["bintrayUser"]?.toString() ?: System.getenv("BINTRAY_USER")
        secretKey = project.properties["bintrayApiKey"]?.toString() ?: System.getenv("BINTRAY_API_KEY")
        repository = "exposed"
        info {
            publish = false
            githubRepo = "https://github.com/JetBrains/Exposed.git"
            vcsUrl = "https://github.com/JetBrains/Exposed.git"
            userOrg = "kotlin"
            license = "Apache-2.0"
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