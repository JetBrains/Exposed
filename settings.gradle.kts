rootProject.name = "exposed"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


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
        // Confusing log in CI build with community-plugin. Use it for local debug
//        println("Using kotlin compiler version: $kotlin_version")
    }
}


pluginManagement {
    repositories {
        google()
        maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "0.9.0"
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
