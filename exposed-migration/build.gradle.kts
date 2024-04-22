plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))

    api(libs.flyway)
    api(libs.flyway.mysql)
    api(libs.flyway.oracle)
    api(libs.flyway.sqlserver)

    testImplementation(project(":exposed-tests"))

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))

    testCompileOnly(libs.pgjdbc.ng)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}
