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
include("exposed-gradle-plugin")

// Route Maven Central through Google Cloud Storage's official mirror to avoid
// the HTTP 429 rate-limit errors TeamCity has been hitting on direct fetches
// from repo.maven.apache.org. The GCS mirror absorbs traffic at a different
// scale and has been confirmed to work reliably from both internal and
// external agents. mavenCentral() is kept as a fallback for the (rare) case
// where the mirror is missing an artifact (Gradle iterates repos on 404 but
// not on 429, so the mirror needs to be first).
pluginManagement {
    repositories {
        google()
        maven("https://maven-central-eu.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven-central-eu.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
}

toolchainManagement {
    jvm {
        javaRepositories {
            repository("foojay") {
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
        }
    }
}
