rootProject.name = "exposed-ktor-crud"

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
