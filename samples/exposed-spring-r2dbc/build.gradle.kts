plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.orgSpringframeworkBoot)
    alias(libs.plugins.ioSpringDependencyManagement)
}

group = "org.jetbrains.exposed"
version = "1.0.0"

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// Exposed requires a newer kotlinx-coroutines than the version managed by Spring Boot,
// so the managed version must be overridden to avoid it being downgraded.
extra["kotlin-coroutines.version"] = libs.versions.kotlinxCoroutines.get()

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(libs.springBootStarterWebFlux)
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxCoroutinesReactor)

    implementation(libs.r2dbcPostgresql)
    implementation(libs.exposedSpringbootR2dbcStarter)

    testImplementation(libs.springBootStarterWebFluxTest)
    testImplementation(libs.springBootTestcontainers)
    testImplementation(libs.testcontainersPostgresql)
    testImplementation(libs.testcontainersR2dbc)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
