import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.exposed.gradle.setupTestDriverDependencies
import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
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

    testImplementation("org.xerial", "sqlite-jdbc", "3.23.1")
    testImplementation("com.h2database", "h2", "1.4.199")
    testImplementation("org.javamoney", "moneta", "1.3")
    testRuntimeOnly("org.testcontainers", "testcontainers", Versions.testContainers)
    setupTestDriverDependencies(dialect) { group, artifactId, version ->
        testImplementation(group, artifactId, version)
    }
}

setupDialectTest(dialect)
