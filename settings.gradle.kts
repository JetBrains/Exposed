rootProject.name = "exposed"
include("exposed-core")
include("exposed-dao")
include("exposed-jodatime")
include("exposed-java-time")
include("spring-transaction")
include("exposed-spring-boot-starter")
include("exposed-jdbc")
include("exposed-tests")
include("exposed-money")
include("exposed-bom")
include("exposed-kotlin-datetime")
include("exposed-crypt")
include("exposed-json")

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
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "0.8.0"
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
