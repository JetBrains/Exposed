plugins {
    kotlin("jvm") apply true
    id("testWithDBs")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
    api(project(":exposed-dao"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc:8.2.2.jre11")
    // Before you ask, the author's work SQL server is running a very old version of MSSQL and newer drivers aren't
    // compatible for whatever reason. He could probably get it working if he wanted to... but he doesn't
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))
}
