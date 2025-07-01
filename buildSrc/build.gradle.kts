repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    gradleApi()
    implementation(libs.jvm)
    implementation(libs.docker.compose)
    implementation(libs.detekt)
}

plugins {
    `kotlin-dsl` apply true
}

gradlePlugin {
    plugins {
        create("testWithDBs") {
            id = "testWithDBs"
            implementationClass = "org.jetbrains.exposed.gradle.DBTestingPlugin"
        }
        create("versionCatalog") {
            id = "exposed-version-catalog"
            implementationClass = "org.jetbrains.exposed.gradle.VersionCatalogPlugin"
        }
    }
}
