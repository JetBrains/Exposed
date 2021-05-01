repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    gradleApi()
    compile("com.avast.gradle", "gradle-docker-compose-plugin", "0.14.2")
    compile("io.github.gradle-nexus", "publish-plugin", "1.0.0")
}

plugins {
    `kotlin-dsl` apply true
}
