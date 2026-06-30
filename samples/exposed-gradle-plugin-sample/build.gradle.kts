// Uncomment alongside the fileVersionFormat default below:
// import org.jetbrains.exposed.v1.gradle.plugin.VersionFormat

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.exposed.plugin") version "1.3.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:1.3.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.3.1")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

kotlin {
    jvmToolchain(17)
}

val dbFile = layout.projectDirectory.file("data/mydb").asFile

exposed {
    migrations {
        tablesPackage.set("com.example.tables")
        databaseUrl.set("jdbc:h2:file:${dbFile.absolutePath}")
        databaseUser.set("sa")
        databasePassword.set("")

        // Defaults — uncomment and tweak to change generated file naming:
        // filePrefix.set("V")
        // fileVersionFormat.set(VersionFormat.TIMESTAMP_ONLY)
        // fileSeparator.set("__")
        // useUpperCaseDescription.set(true)
        // fileExtension.set(".sql")
    }
}
