repositories {
    jcenter()
}

dependencies {
    gradleApi()
    compile("com.avast.gradle", "gradle-docker-compose-plugin", "0.9.3")
}

plugins {
    `kotlin-dsl` apply true
    id("tanvd.kosogor") version "1.0.9" apply true
}
