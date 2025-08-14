val exposedVersion: String by project
val h2Version: String by project
val flywayVersion: String by project

plugins {
    id("application")
    kotlin("jvm") version "2.2.0"
}

group = "org.jetbrains.exposed.samples.migration"
version = "0.0.1"

application {
    mainClass = "ApplicationKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-migration:$exposedVersion")

    implementation("com.h2database:h2:$h2Version")

    implementation("org.flywaydb:flyway-core:$flywayVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.register<JavaExec>("generateMigrationScript") {
    group = "application"
    description = "Generate migration script in the path exposed-migration/migrations"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "GenerateMigrationScriptKt"
}
