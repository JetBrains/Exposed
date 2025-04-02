import org.gradle.kotlin.dsl.maven
import org.jetbrains.exposed.gradle.configureDetekt
import org.jetbrains.exposed.gradle.configurePublishing
import org.jetbrains.exposed.gradle.testDb
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val buildScriptLogger: Logger = Logging.getLogger("buildScriptLogger")

/* Kotlin-community project related parameter settings */

val kotlin_version: String by project
val kotlin_repo_url: String? by project
val kotlin_language_version: String? by project
val kotlin_api_version: String? by project
buildScriptLogger.log(LogLevel.INFO, "kotlin Api Version : $kotlin_api_version ")
buildScriptLogger.log(LogLevel.INFO, "kotlin Language Version : $kotlin_language_version ")
buildScriptLogger.log(LogLevel.INFO, "kotlin repository url : $kotlin_repo_url ")

plugins {
    kotlin("jvm") apply true
    id(libs.plugins.detekt.get().pluginId) apply true
    alias(libs.plugins.binary.compatibility.validator)
    id(libs.plugins.docker.compose.get().pluginId)

    alias(libs.plugins.dokka)
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

val kotlinRepoUrl: String? = providers.gradleProperty("kotlin_repo_url").orNull
repositories {
    if (kotlinRepoUrl?.isNotEmpty() == true) {
        maven(kotlinRepoUrl)
    }
    maven("https://redirector.kotlinlang.org/maven/dev")
    mavenLocal()
    mavenCentral()
}

configurations.configureEach {
    if (isCanBeResolved) {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(kotlin_version)
            }
        }
    }
}

allprojects {
    if (this.name != "exposed-tests" && this.name != "exposed-bom" && this != rootProject) {
        configurePublishing()
    }
}

apiValidation {
    ignoredProjects.addAll(listOf("exposed-tests", "exposed-bom"))
}

subprojects {
    configureDetekt()

    dependencies {
        detektPlugins(rootProject.libs.detekt.formatting)
    }

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        languageVersion = KotlinVersion.fromVersion(languageVersion.toString())
        apiVersion = KotlinVersion.fromVersion(apiVersion.toString())

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
        colima = true
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
