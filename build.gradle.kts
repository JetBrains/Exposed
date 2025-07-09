import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.exposed.gradle.configureDetekt
import org.jetbrains.exposed.gradle.configureMavenCentralMetadata
import org.jetbrains.exposed.gradle.testDb
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.time.Year

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
    alias(libs.plugins.kover)
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(project.file("docs/api"))
    }
    pluginsConfiguration {
        html {
            customAssets.from("docs/dokka/logo-icon.svg")
            footerMessage.set("© ${Year.now()} JetBrains s.r.o and contributors. Apache License 2.0")
        }
    }
}

dependencies {
    dokka(projects.exposed.exposedCore)
    dokka(projects.exposed.exposedCrypt)
    dokka(projects.exposed.exposedDao)
    dokka(projects.exposed.exposedPluginCore)
    dokka(projects.exposed.exposedGradlePlugin)
    dokka(projects.exposed.exposedMavenPlugin)
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
    dokka(projects.exposed.exposedSpringBoot4Starter)
    dokka(projects.exposed.springTransaction)
    dokka(projects.exposed.spring7Transaction)
    dokka(projects.exposed.exposedDaoR2dbc)

    // Kover aggregated coverage dependencies
    // Include all source modules for coverage aggregation
    kover(project(":exposed-core"))
    kover(project(":exposed-dao"))
    kover(project(":exposed-plugin-core"))
    kover(project(":exposed-gradle-plugin"))
    kover(project(":exposed-maven-plugin"))
    kover(project(":exposed-jodatime"))
    kover(project(":exposed-java-time"))
    kover(project(":spring-transaction"))
    kover(project(":spring7-transaction"))
    kover(project(":exposed-spring-boot-starter"))
    kover(project(":exposed-spring-boot4-starter"))
    kover(project(":exposed-jdbc"))
    kover(project(":exposed-money"))
    kover(project(":exposed-kotlin-datetime"))
    kover(project(":exposed-crypt"))
    kover(project(":exposed-json"))
    kover(project(":exposed-migration-core"))
    kover(project(":exposed-migration-jdbc"))
    kover(project(":exposed-migration-r2dbc"))
    kover(project(":exposed-r2dbc"))
    kover(project(":exposed-dao-r2dbc"))

    // Include test modules to ensure their tests are executed and coverage is collected
    kover(project(":exposed-tests"))
    kover(project(":exposed-r2dbc-tests"))
    kover(project(":exposed-dao-r2dbc-tests"))
}

repositories {
    val kotlinRepoUrl: String? = providers.gradleProperty("kotlin_repo_url").orNull
    if (kotlinRepoUrl?.isNotEmpty() == true) {
        maven(kotlinRepoUrl)
    }
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
    if (this.name != "exposed-tests" &&
        this.name != "exposed-r2dbc-tests" &&
        this.name != "exposed-jdbc-r2dbc-tests" &&
        this.name != "exposed-dao-r2dbc-tests" &&
        this != rootProject
    ) {
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
    ignoredProjects.addAll(
        listOf("exposed-tests", "exposed-bom", "exposed-r2dbc-tests", "exposed-jdbc-r2dbc-tests", "exposed-version-catalog", "exposed-dao-r2dbc-tests")
    )
}

subprojects {

    repositories {
        val kotlinRepoUrl: String? = providers.gradleProperty("kotlin_repo_url").orNull
        if (kotlinRepoUrl?.isNotEmpty() == true) {
            maven(kotlinRepoUrl)
        }
        maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    }

    configureDetekt()

    dependencies {
        detektPlugins(rootProject.libs.detekt.formatting)
    }

    if (name == "exposed-bom") return@subprojects

    apply(plugin = rootProject.libs.plugins.jvm.get().pluginId)
    apply(plugin = rootProject.libs.plugins.kover.get().pluginId)
    tasks.withType<Detekt>().configureEach {
        // Detekt's bundled parser can't handle context parameter syntax (context(_: X)).
        exclude("**/MavenProjectGeneration.kt")
    }

}

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
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

        }
    }

subprojects {
    if (name == "exposed-bom" || name == "exposed-version-catalog") return@subprojects

    apply(plugin = rootProject.libs.plugins.jvm.get().pluginId)
    apply(plugin = rootProject.libs.plugins.kover.get().pluginId)

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

    testDb("mariadb") {
        dialects("MARIADB")
        container = "mariadb"
        port = 3000
        dependencies {
            dependency(rootProject.libs.mariadb)
        }
    }

    testDb("oracle") {
        port = 3003
        dialects("ORACLE")
        dependencies {
            dependency(rootProject.libs.oracle)
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

// Configure Kover for aggregated project coverage
kover {
    reports {
        total {
            // Generate HTML report
            html {
                onCheck.set(true)
            }
            // Generate XML report for CI/CD integration
            xml {
                onCheck.set(true)
            }
            // Generate verification report
            verify {
                onCheck.set(true)
            }
        }
    }
}
