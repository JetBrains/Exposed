import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.jetbrains.exposed.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") apply true
    id(libs.plugins.detekt.get().pluginId) apply true
    alias(libs.plugins.binary.compatibility.validator)
    id(libs.plugins.docker.compose.get().pluginId)
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
        detektPlugins(rootProject.libs.detekt.formatting)
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

    apply(plugin = rootProject.libs.plugins.jvm.get().pluginId)

    testDb("h2") {
        withContainer = false
        dialects("H2", "H2_MYSQL", "H2_PSQL", "H2_MARIADB", "H2_ORACLE", "H2_SQLSERVER")

        dependencies {
            dependency(rootProject.libs.h2)
        }
    }

    testDb("h2_v1") {
        withContainer = false
        dialects("H2", "H2_MYSQL")

        dependencies {
            dependency(rootProject.libs.h1)
        }
    }

    testDb("sqlite") {
        withContainer = false
        dialects("sqlite")

        dependencies {
            dependency(rootProject.libs.sqlite.jdbc)
        }
    }

    testDb("mysql") {
        port = 3001
        dialects("mysql")
        dependencies {
            dependency(rootProject.libs.mysql51)
        }
    }

    testDb("mysql8") {
        port = 3002
        dialects("mysql")
        dependencies {
            dependency(rootProject.libs.mysql)
        }
    }

    testDb("mariadb_v2") {
        dialects("mariadb")
        container = "mariadb"
        port = 3000
        dependencies {
            dependency(rootProject.libs.maria.db2)
        }
    }

    testDb("mariadb_v3") {
        dialects("mariadb")
        container = "mariadb"
        port = 3000
        dependencies {
            dependency(rootProject.libs.maria.db3)
        }
    }

    testDb("oracle") {
        port = 3003
        colima = true
        dialects("oracle")
        dependencies {
            dependency(rootProject.libs.oracle12)
        }
    }

    testDb("postgres") {
        port = 3004
        dialects("postgresql")
        dependencies {
            dependency(rootProject.libs.postgre)
        }
    }

    testDb("postgresNG") {
        port = 3004
        dialects("postgresqlng")
        container = "postgres"
        dependencies {
            dependency(rootProject.libs.postgre)
            dependency(rootProject.libs.pgjdbc.ng)
        }
    }

    testDb("sqlserver") {
        port = 3005
        dialects("sqlserver")
        dependencies {
            dependency(rootProject.libs.mssql)
        }
    }
}
