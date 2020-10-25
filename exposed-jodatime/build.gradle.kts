import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.exposed.gradle.Versions
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
    api("joda-time", "joda-time", "2.10.5")
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))

    testImplementation("com.opentable.components", "otj-pg-embedded", "0.12.0")
    testImplementation("org.xerial", "sqlite-jdbc", Versions.sqlLite3)
    testImplementation("com.h2database", "h2", Versions.h2)
    testRuntimeOnly("org.testcontainers", "testcontainers", "1.14.3")

    when (dialect) {
        "mariadb" ->    testImplementation("org.mariadb.jdbc", "mariadb-java-client", Versions.mariaDB)
        "mysql" ->      testImplementation("mysql", "mysql-connector-java", Versions.mysql51)
        "mysql8" ->     testImplementation("mysql", "mysql-connector-java", Versions.mysql80)
        "oracle" ->     testImplementation("com.oracle.database.jdbc", "ojdbc8", Versions.oracle12)
        "sqlserver" ->  testImplementation("com.microsoft.sqlserver", "mssql-jdbc", Versions.sqlserver)
        else -> {
            testImplementation("com.h2database", "h2", Versions.h2)
            testImplementation("mysql", "mysql-connector-java", Versions.mysql51)
            testImplementation("org.postgresql", "postgresql", Versions.postgre)
            testImplementation("com.impossibl.pgjdbc-ng", "pgjdbc-ng", Versions.postgreNG)
        }
    }
}

publishJar {
    publication {
        artifactId = "exposed-jodatime"
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

setupDialectTest(dialect)