rootProject.name = "exposed"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include("exposed-core")
include("exposed-dao")
include("exposed-jodatime")
include("exposed-java-time")
include("spring-transaction")
include("spring7-transaction")
include("exposed-spring-boot-starter")
include("exposed-spring-boot4-starter")
include("exposed-jdbc")
include("exposed-tests")
include("exposed-money")
include("exposed-bom")
include("exposed-kotlin-datetime")
include("exposed-crypt")
include("exposed-json")
include("exposed-migration-core")
include("exposed-migration-jdbc")
include("exposed-migration-r2dbc")
include("exposed-r2dbc")
include("exposed-r2dbc-tests")
include("exposed-jdbc-r2dbc-tests")
include("exposed-plugin-core")
include("exposed-gradle-plugin")
include("exposed-maven-plugin")
include("exposed-version-catalog")

val kotlin_version: String by settings

dependencyResolutionManagement {
    versionCatalogs {
        val libs by creating {
            if (extra.has("kotlin_version")) {
                val kotlinVersion = extra["kotlin_version"].toString()
                version("kotlin-version", kotlinVersion)
            }
        }
    }
}

pluginManagement.resolutionStrategy.eachPlugin {
    if (requested.id.id.startsWith("org.jetbrains.kotlin.")) {
        useVersion(kotlin_version)
        println("Using kotlin compiler version: $kotlin_version")
    }
}


pluginManagement {
    repositories {
        val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
        val kotlin_version: String? by settings
        val repoUrl = kotlinRepoUrl
        val kotlinVersion = kotlin_version
        if (!repoUrl.isNullOrEmpty() && !kotlinVersion.isNullOrEmpty()) {
            exclusiveContent {
                forRepository {
                    maven(repoUrl)
                }
                filter {
                    includeVersionByRegex("org\\.jetbrains\\.kotlin.*", ".*", kotlinVersion)
                }
            }
        }
        google()
        maven("https://maven-central-eu.storage-download.googleapis.com/maven2/")
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
        val kotlin_version: String? by settings
        val repoUrl = kotlinRepoUrl
        val kotlinVersion = kotlin_version
        if (!repoUrl.isNullOrEmpty() && !kotlinVersion.isNullOrEmpty()) {
            exclusiveContent {
                forRepository {
                    maven(repoUrl)
                }
                filter {
                    includeVersionByRegex("org\\.jetbrains\\.kotlin.*", ".*", kotlinVersion)
                }
            }
        }
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
        google()
        maven("https://maven-central-eu.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
