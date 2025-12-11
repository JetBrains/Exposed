import com.vanniktech.maven.publish.MavenPublishBaseExtension
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
    alias(libs.plugins.kover)
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

    // Kover aggregated coverage dependencies
    // Include all source modules for coverage aggregation
    kover(project(":exposed-core"))
    kover(project(":exposed-dao"))
    kover(project(":exposed-jodatime"))
    kover(project(":exposed-java-time"))
    kover(project(":spring-transaction"))
    kover(project(":exposed-spring-boot-starter"))
    kover(project(":exposed-jdbc"))
    kover(project(":exposed-money"))
    kover(project(":exposed-kotlin-datetime"))
    kover(project(":exposed-crypt"))
    kover(project(":exposed-json"))
    kover(project(":exposed-migration-core"))
    kover(project(":exposed-migration-jdbc"))
    kover(project(":exposed-migration-r2dbc"))
    kover(project(":exposed-r2dbc"))

    // Include test modules to ensure their tests are executed and coverage is collected
    kover(project(":exposed-tests"))
    kover(project(":exposed-r2dbc-tests"))
}

repositories {
    mavenLocal()
    mavenCentral()
}

allprojects {
    if (this.name != "exposed-tests" &&
        this.name != "exposed-r2dbc-tests" &&
        this.name != "exposed-jdbc-r2dbc-tests" &&
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
    ignoredProjects.addAll(listOf("exposed-tests", "exposed-bom", "exposed-r2dbc-tests", "exposed-jdbc-r2dbc-tests"))
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
