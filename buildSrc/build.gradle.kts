repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    gradleApi()
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:1.5.21")
    implementation("com.avast.gradle", "gradle-docker-compose-plugin", "0.14.2")
    implementation("io.github.gradle-nexus", "publish-plugin", "1.0.0")
    implementation("io.gitlab.arturbosch.detekt", "detekt-gradle-plugin", "1.17.1")
}

plugins {
    `kotlin-dsl` apply true
}
