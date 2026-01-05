val exposed_version: String by project
val h2_version: String by project
val slf4j_version: String by project

plugins {
    kotlin("jvm") version "2.2.20"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version") // Optional
    implementation("com.h2database:h2:$h2_version")
    implementation("org.slf4j:slf4j-nop:$slf4j_version")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
