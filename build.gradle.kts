import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.jetbrains.exposed.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") apply true
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.2"

    id("com.avast.gradle.docker-compose")
}

repositories {
    mavenLocal()
    mavenCentral()
}

allprojects {
    configureDetekt()

    if (this.name != "exposed-tests" && this.name != "exposed-bom" && this != rootProject) {
        configurePublishing()
    }
}

apiValidation {
    ignoredProjects.addAll(listOf("exposed-tests", "exposed-bom"))
}

val reportMerge by tasks.registering(ReportMergeTask::class) {
    output.set(rootProject.buildDir.resolve("reports/detekt/exposed.xml"))
}

subprojects {
    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt", "detekt-formatting", "1.23.3")
    }
    tasks.withType<Detekt>().configureEach detekt@{
        finalizedBy(reportMerge)
        reportMerge.configure {
            input.from(this@detekt.xmlReportFile)
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
            apiVersion = "1.6"
            languageVersion = "1.6"
        }
    }
}

subprojects {
    if (name == "exposed-bom") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")

    testDb("h2") {
        withContainer = false
        dialects("H2", "H2_MYSQL", "H2_PSQL", "H2_MARIADB", "H2_ORACLE", "H2_SQLSERVER")

        dependencies {
            dependency("com.h2database:h2:${Versions.h2_v2}")
        }
    }

    testDb("h2_v1") {
        withContainer = false
        dialects("H2", "H2_MYSQL")

        dependencies {
            dependency("com.h2database:h2:${Versions.h2}")
        }
    }

    testDb("sqlite") {
        withContainer = false
        dialects("sqlite")

        dependencies {
            dependency("org.xerial:sqlite-jdbc:${Versions.sqlLite3}")
        }
    }

    testDb("mysql") {
        port = 3001
        dialects("mysql")
        dependencies {
            dependency("mysql:mysql-connector-java:${Versions.mysql51}")
        }
    }

    testDb("mysql8") {
        port = 3002
        dialects("mysql")
        dependencies {
            dependency("mysql:mysql-connector-java:${Versions.mysql80}")
        }
    }

    testDb("mariadb_v2") {
        dialects("mariadb")
        container = "mariadb"
        port = 3000
        dependencies {
            dependency("org.mariadb.jdbc:mariadb-java-client:${Versions.mariaDB_v2}")
        }
    }

    testDb("mariadb_v3") {
        dialects("mariadb")
        container = "mariadb"
        port = 3000
        dependencies {
            dependency("org.mariadb.jdbc:mariadb-java-client:${Versions.mariaDB_v3}")
        }
    }

    testDb("oracle") {
        port = 3003
        colima = true
        dialects("oracle")
        dependencies {
            dependency("com.oracle.database.jdbc:ojdbc8:${Versions.oracle12}")
        }
    }

    testDb("postgres") {
        port = 3004
        dialects("postgresql")
        dependencies {
            dependency("org.postgresql:postgresql:${Versions.postgre}")
        }
    }

    testDb("postgresNG") {
        port = 3004
        dialects("postgresqlng")
        container = "postgres"
        dependencies {
            dependency("org.postgresql:postgresql:${Versions.postgre}")
            dependency("com.impossibl.pgjdbc-ng:pgjdbc-ng:${Versions.postgreNG}")
        }
    }

    testDb("sqlserver") {
        port = 3005
        dialects("sqlserver")
        dependencies {
            dependency("com.microsoft.sqlserver:mssql-jdbc:${Versions.sqlserver}")
        }
    }
}
