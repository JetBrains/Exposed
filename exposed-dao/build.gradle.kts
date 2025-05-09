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

    // TODO change dependency level (use api or at minimum implementation dep)
    compileOnly(project(":exposed-jdbc"))
}
