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
include("exposed-migration")
include("exposed-r2dbc")

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
