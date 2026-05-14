import org.jetbrains.exposed.v1.gradle.plugin.VersionFormat

plugins {
    alias(libs.plugins.jvm)

    id("org.jetbrains.exposed.plugin") version "1.3.0"

    application
}

val dbFile = layout.projectDirectory.file("data/exampledb").asFile

exposed {
    migrations {
        tablesPackage.set("org.example.tables")

        databaseUrl.set("jdbc:h2:file:${dbFile.absolutePath}")
        databaseUser.set("sa")
        databasePassword.set("")

        // optional configurations
        fileDirectory.set(layout.projectDirectory.dir("src/main/resources/db"))
        filePrefix.set("V")
        fileVersionFormat.set(VersionFormat.MAJOR_MINOR)
        fileSeparator.set("--")
        useUpperCaseDescription.set(false)
    }
}

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(libs.h2)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "org.example.AppKt"
}
