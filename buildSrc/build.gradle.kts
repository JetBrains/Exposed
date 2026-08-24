repositories {
    val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
    val kotlin_version = providers.gradleProperty("kotlin_version").orNull
    val repoUrl = kotlinRepoUrl
    val kotlinVersion = kotlin_version
    if (!repoUrl.isNullOrEmpty() && !kotlinVersion.isNullOrEmpty()) {
        exclusiveContent {
            forRepository {
                maven(repoUrl)
            }
            filter {
                includeVersionByRegex("org\\.jetbrains\\.kotlin.*", ".*", kotlinVersion)
            }
        }
    }
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
}

dependencies {
    gradleApi()
    implementation(libs.jvm)
    implementation(libs.docker.compose)
    implementation(libs.detekt)
    implementation(libs.maven.publish)
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
    }
}
