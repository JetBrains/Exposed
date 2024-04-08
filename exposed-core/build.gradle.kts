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
    api(kotlin("stdlib"))
    api(kotlin("reflect"))
    api(libs.kotlinx.coroutines)
    api(libs.slf4j)
    api(libs.flyway)
    api(libs.flyway.mysql)
    api(libs.flyway.oracle)
    api(libs.flyway.sqlserver)
}
