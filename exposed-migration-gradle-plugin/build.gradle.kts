plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())

    implementation(projects.exposed.exposedJdbc)
    implementation(projects.exposed.exposedMigration)

    implementation(libs.flyway.postgresql)
    implementation(libs.flyway.mysql)
    implementation(libs.flyway.sqlserver)
    implementation(libs.flyway.oracle)

    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.mysql)
    implementation(libs.testcontainers.mariadb)
    implementation(libs.testcontainers.mssqlserver)
    implementation(libs.testcontainers.oracle)

    implementation(libs.h2)
    implementation(libs.postgre)
    implementation(libs.mysql)
    implementation(libs.maria.db3)
    implementation(libs.mssql)
    implementation(libs.oracle19)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}
