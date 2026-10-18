rootProject.name = "exposed-spring-data-native"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // provisions the JDK 17 toolchain and, if no GraalVM is configured, a native-image capable JDK (see build.gradle.kts)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Builds Exposed from this checkout: org.jetbrains.exposed:* dependencies are substituted with the projects of the root build,
// so the sample always uses the current sources of exposed-spring-data (which isn't published yet).
includeBuild("../..")
