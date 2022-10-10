plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
    api("org.springframework.security", "spring-security-crypto", "5.7.3")
}
