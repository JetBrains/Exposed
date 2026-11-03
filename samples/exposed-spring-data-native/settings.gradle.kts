rootProject.name = "exposed-spring-data-native"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Builds Exposed from this checkout: org.jetbrains.exposed:* dependencies are substituted with the projects of the root build,
// so the sample always uses the current sources of exposed-spring-data (which isn't published yet).
includeBuild("../..")
