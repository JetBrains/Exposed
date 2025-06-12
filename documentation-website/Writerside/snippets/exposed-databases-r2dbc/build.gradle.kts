plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.serialization)

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // ...
    // Use the Kotlin JUnit 5 integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Use the JUnit 5 integration.
    testImplementation(libs.junit.jupiter.engine)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
    implementation(libs.exposed.core)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.json)
    implementation(libs.exposed.r2dbc)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.oracle)
    implementation(libs.r2dbc.h2)
    implementation(libs.r2dbc.mariadb)
    implementation(libs.r2dbc.sqlserver)
    implementation(libs.r2dbc.mysql)
    implementation(libs.slf4j)
    implementation(libs.jackson.kotlin)
    implementation(libs.postgresql)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    // Define the main class for the application.
    mainClass = "org.example.AppKt"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
