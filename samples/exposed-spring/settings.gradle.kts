rootProject.name = "exposed-spring"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("exposedLibs") {
            from("org.jetbrains.exposed:exposed-version-catalog:1.3.2")
        }
    }
}
