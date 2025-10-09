plugins {
    kotlin("jvm") version "2.0.20"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:1.0.0-rc-2")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0-rc-2")
    implementation("org.jetbrains.exposed:exposed-dao:1.0.0-rc-2") // Optional
    implementation("com.h2database:h2:2.2.224")
    implementation("org.slf4j:slf4j-nop:1.7.30")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
