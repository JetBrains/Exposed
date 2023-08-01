plugins {
    kotlin("jvm") apply true
    id("org.jetbrains.exposed.gradle.conventions.dokka")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
    api("org.springframework.security", "spring-security-crypto", "5.7.3")
}
