pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    versionCatalogs {
        create("exposedLibs") {
            from("org.jetbrains.exposed:exposed-version-catalog:1.3.2")
        }
    }
}

rootProject.name = "exposed-gradle-plugin-sample"
