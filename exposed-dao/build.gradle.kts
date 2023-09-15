plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    api(project(":exposed-core"))
}
