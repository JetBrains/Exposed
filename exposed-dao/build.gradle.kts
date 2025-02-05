plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    api(project(":exposed-core"))
    // how to avoid this
    compileOnly(project(":exposed-jdbc"))
}
