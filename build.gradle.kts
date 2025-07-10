import org.jetbrains.exposed.gradle.configureDetekt
import org.jetbrains.exposed.gradle.configureMavenCentralMetadata
import org.jetbrains.exposed.gradle.testDb

plugins {
    kotlin("jvm") apply true
    id(libs.plugins.detekt.get().pluginId) apply true
    alias(libs.plugins.binary.compatibility.validator)
    id(libs.plugins.docker.compose.get().pluginId)

    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(project.file("docs/api"))
    }
}

dependencies {
    dokka(projects.exposed.exposedCore)
    dokka(projects.exposed.exposedCrypt)
    dokka(projects.exposed.exposedDao)
    dokka(projects.exposed.exposedJavaTime)
    dokka(projects.exposed.exposedJdbc)
    dokka(projects.exposed.exposedJodatime)
    dokka(projects.exposed.exposedJson)
    dokka(projects.exposed.exposedKotlinDatetime)
    dokka(projects.exposed.exposedMigration)
    dokka(projects.exposed.exposedMoney)
    dokka(projects.exposed.exposedSpringBootStarter)
    dokka(projects.exposed.springTransaction)
}

repositories {
    mavenLocal()
    mavenCentral()
}

allprojects {
    if (this.name != "exposed-tests" && this.name != "exposed-r2dbc-tests" && this != rootProject) {
        apply(plugin = "com.vanniktech.maven.publish")
        this@allprojects.mavenPublishing {
            pom {
                configureMavenCentralMetadata(this@allprojects)
            }

            publishToMavenCentral()
            signAllPublications()
        }
    }
}

apiValidation {
    ignoredProjects.addAll(listOf("exposed-tests", "exposed-bom", "exposed-r2dbc-tests"))
}

subprojects {
    configureDetekt()

    dependencies {
        detektPlugins(rootProject.libs.detekt.formatting)
    }
}

subprojects {
    if (name == "exposed-bom") return@subprojects

    apply(plugin = rootProject.libs.plugins.jvm.get().pluginId)

    testDb("h2_v2") {
        withContainer = false
        dialects("H2_V2", "H2_V2_MYSQL", "H2_V2_PSQL", "H2_V2_MARIADB", "H2_V2_ORACLE", "H2_V2_SQLSERVER")

        dependencies {
            dependency(rootProject.libs.h2)
        }
    }

    testDb("h2_v1") {
        withContainer = false
        dialects("H2_V1", "H2_V1_MYSQL")

        dependencies {
            dependency(rootProject.libs.h1)
        }
    }

    testDb("sqlite") {
        withContainer = false
        dialects("SQLITE")

        dependencies {
            dependency(rootProject.libs.sqlite.jdbc)
        }
    }

    testDb("mysql_v5") {
        port = 3001
        container = "mysql5"
        dialects("MYSQL_V5")
        dependencies {
            dependency(rootProject.libs.mysql51)
        }
    }

    testDb("mysql_v8") {
        port = 3002
        container = "mysql8"
        dialects("MYSQL_V8")
        dependencies {
            dependency(rootProject.libs.mysql)
        }
    }

    testDb("mariadb_v2") {
        dialects("MARIADB")
        container = "mariadb"
        port = 3000
        dependencies {
            dependency(rootProject.libs.maria.db2)
        }
    }

    testDb("mariadb_v3") {
        dialects("MARIADB")
        container = "mariadb"
        port = 3000
        dependencies {
            dependency(rootProject.libs.maria.db3)
        }
    }

    testDb("oracle") {
        port = 3003
        dialects("ORACLE")
        dependencies {
            dependency(rootProject.libs.oracle19)
        }
    }

    testDb("postgres") {
        port = 3004
        dialects("POSTGRESQL")
        dependencies {
            dependency(rootProject.libs.postgre)
        }
    }

    testDb("postgresNG") {
        port = 3004
        dialects("POSTGRESQLNG")
        container = "postgres"
        dependencies {
            dependency(rootProject.libs.postgre)
            dependency(rootProject.libs.pgjdbc.ng)
        }
    }

    testDb("sqlserver") {
        port = 3005
        dialects("SQLSERVER")
        dependencies {
            dependency(rootProject.libs.mssql)
        }
    }
}
