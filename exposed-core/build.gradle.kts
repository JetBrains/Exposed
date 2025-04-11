import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("jvm")

    alias(libs.plugins.dokka)
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
}

// TODO: Remove this if not applied for tests
tasks.named<KotlinCompilationTask<*>>("compileKotlin").configure {
    compilerOptions.optIn.add("org.jetbrains.exposed.sql.InternalApi")
}
