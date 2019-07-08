plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
}