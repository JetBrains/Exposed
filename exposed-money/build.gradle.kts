import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.exposed.gradle.setupTestDriverDependencies
import tanvd.kosogor.proxy.publishJar

plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
}

val dialect: String by project

dependencies {
    api(project(":exposed-core"))
    api(project(":exposed-dao"))
    api("javax.money", "money-api", "1.0.3")
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))

    testImplementation("com.opentable.components", "otj-pg-embedded", "0.12.0")
    testImplementation("org.xerial", "sqlite-jdbc", "3.23.1")
    testImplementation("com.h2database", "h2", "1.4.199")
    testImplementation("org.javamoney", "moneta", "1.3")
    testRuntimeOnly("org.testcontainers", "testcontainers", "1.14.3")
    setupTestDriverDependencies(dialect) { group, artifactId, version ->
        testImplementation(group, artifactId, version)
    }
}

publishJar {
    publication {
        artifactId = "exposed-money"
    }

    bintray {
        username = project.properties["bintrayUser"]?.toString() ?: System.getenv("BINTRAY_USER")
        secretKey = project.properties["bintrayApiKey"]?.toString() ?: System.getenv("BINTRAY_API_KEY")
        repository = "exposed"
        info {
            publish = false
            githubRepo = "https://github.com/JetBrains/Exposed.git"
            vcsUrl = "https://github.com/JetBrains/Exposed.git"
            userOrg = "kotlin"
            license = "Apache-2.0"
        }
    }
}

setupDialectTest(dialect)