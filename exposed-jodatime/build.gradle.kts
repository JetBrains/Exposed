plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
}

dependencies {
    api(project(":exposed-core"))
    api("joda-time", "joda-time", "2.10.2")
}