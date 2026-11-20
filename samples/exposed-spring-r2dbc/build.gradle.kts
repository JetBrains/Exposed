plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.orgSpringframeworkBoot)
    alias(libs.plugins.ioSpringDependencyManagement)
    alias(libs.plugins.orgGraalvmBuildtoolsNative)
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

// Spring Boot 4 requires GraalVM 25 or later to build a native image, regardless of the JVM toolchain used above
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    environment.put("BP_JVM_VERSION", "25")
}
