plugins {
    kotlin("jvm")

    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
    val kotlinRepoUrl: String? = providers.gradleProperty("kotlin_repo_url").orNull
    if (kotlinRepoUrl?.isNotEmpty() == true) {
        maven(kotlinRepoUrl)
    }
}

kotlin {
    jvmToolchain(8)

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(kotlin("stdlib"))
    api(kotlin("reflect"))
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.jvm.datetime)
    api(libs.slf4j)
}
