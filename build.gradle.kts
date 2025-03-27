import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.exposed.gradle.configureDetekt
import org.jetbrains.exposed.gradle.configureMavenCentralMetadata
import org.jetbrains.exposed.gradle.testDb
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val buildScriptLogger: Logger = Logging.getLogger("buildScriptLogger")

/* Kotlin-community project related parameter settings */

val kotlin_version: String by project
val kotlin_repo_url: String? by project

val kotlin_language_version = providers.gradleProperty("kotlin_language_version")
    .map(org.jetbrains.kotlin.gradle.dsl.KotlinVersion::fromVersion)
    .orNull

val kotlin_api_version = providers.gradleProperty("kotlin_api_version")
    .map(org.jetbrains.kotlin.gradle.dsl.KotlinVersion::fromVersion)
    .orNull

val warnings_as_errors = providers.gradleProperty("kotlin_Werror_override").map {
    when (it) {
        "enable" -> true
        "disable" -> false
        else -> error("Unexpected value for 'kotlin_Werror_override' property: $it")
    }
}

// infrastructure for builds as a Kotlin user project
// ("implement a Gradle property for passing additional CLI options to the Kotlin compiler")
val kotlin_additional_cli_options =
    providers.gradleProperty("kotlin_additional_cli_options").orNull
        ?.split(' ')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)

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
    dokka(projects.exposed.exposedMigrationCore)
    dokka(projects.exposed.exposedMigrationJdbc)
    dokka(projects.exposed.exposedMigrationR2dbc)
    dokka(projects.exposed.exposedMoney)
    dokka(projects.exposed.exposedR2dbc)
    dokka(projects.exposed.exposedSpringBootStarter)
    dokka(projects.exposed.springTransaction)
}

repositories {
    maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    maven("https://packages.jetbrains.team/maven/p/kt/dev")
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
    if (this.name != "exposed-tests" && this.name != "exposed-r2dbc-tests" && this != rootProject) {
        apply(plugin = "com.vanniktech.maven.publish")
        apply(plugin = "signing")
        this@allprojects.mavenPublishing {
            pom {
                configureMavenCentralMetadata(this@allprojects)
            }

            publishToMavenCentral(automaticRelease = true)
            signPublicationIfKeyPresent(this@allprojects, this)
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

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        apiVersion.set(kotlin_api_version)
        languageVersion.set(kotlin_language_version)

        buildScriptLogger.log(LogLevel.INFO, "kotlin Api Version : $kotlin_api_version ")
        buildScriptLogger.log(LogLevel.INFO, "kotlin Language Version : $kotlin_language_version ")
        buildScriptLogger.log(LogLevel.INFO, "kotlin repository url : $kotlin_repo_url ")

        println("Additional cli options ${kotlin_additional_cli_options?.joinToString(" ")}")

        if (kotlin_additional_cli_options != null) {
            compilerOptions.freeCompilerArgs.addAll(kotlin_additional_cli_options)
            logger.info("<KUP> additional cli options added: ${kotlin_additional_cli_options.joinToString(" ")}")
        }

        freeCompilerArgs.add("-Xreport-all-warnings")
        buildScriptLogger.log(LogLevel.INFO, "-Xreport-all-warnings enabled")

        allWarningsAsErrors.set(warnings_as_errors)
        buildScriptLogger.log(LogLevel.INFO, "-Werror state: $warnings_as_errors")

        freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
        buildScriptLogger.log(LogLevel.INFO, "-Xrender-internal-diagnostic-names enabled")

        freeCompilerArgs.add("-Wextra")
        buildScriptLogger.log(LogLevel.INFO, "-Wextra enabled")

        freeCompilerArgs.add("-Xuse-fir-experimental-checkers")
        buildScriptLogger.log(LogLevel.INFO, "-Xuse-fir-experimental-checkers enabled")

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

fun signPublicationIfKeyPresent(project: Project, publication: MavenPublishBaseExtension) {
    val keyId = System.getenv("SIGNING_KEY_ID")
    val signingKey = System.getenv("SIGNING_KEY")
    val signingKeyPassphrase = System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrBlank()) {
        println("In-memory GPG key found. Signing artifacts for ${project.path}.")
        project.extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, preprocessPrivateGpgKey(signingKey), signingKeyPassphrase)
            publication.signAllPublications()
        }
    }
}

private fun preprocessPrivateGpgKey(key: String): String {
    val prefix = "-----BEGIN PGP PRIVATE KEY BLOCK-----"
    val suffix = "-----END PGP PRIVATE KEY BLOCK-----"
    val delimiter = "\r\n"
    return prefix + delimiter + key
        .replace(prefix, "")
        .replace(suffix, "")
        .replace(" ", "\r\n") + delimiter + suffix
}
