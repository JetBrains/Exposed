import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-jdbc"))


    testImplementation(platform("org.junit:junit-bom:5.8.0"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.platform:junit-platform-engine")
    testImplementation("org.assertj:assertj-core:3.21.0")
    testImplementation("org.postgresql:postgresql:${Versions.postgre}")

    //test containers
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.16.0"))
    testImplementation("org.testcontainers:postgresql")
}

tasks {
    test {
        useJUnitPlatform {
            includeEngines("junit-jupiter")
            testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            testLogging.showStandardStreams = true
        }
    }
}
