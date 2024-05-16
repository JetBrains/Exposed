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
    compileOnly(libs.postgre)
    compileOnly(libs.oracle12)
    api(libs.kotlinx.coroutines)
    api(libs.slf4j)
}
