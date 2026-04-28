import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    application
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("org.jetbrains.exposed.v1.perf.MainKt")
}

dependencies {
    implementation(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-r2dbc"))

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.r2dbc.spi)

    runtimeOnly(libs.h2)
    runtimeOnly(libs.r2dbc.h2) {
        exclude(group = "com.h2database", module = "h2")
    }
    implementation(libs.h2)
    implementation(libs.r2dbc.h2) {
        exclude(group = "com.h2database", module = "h2")
    }

    implementation(libs.slf4j)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

jmh {
    warmupIterations.set(5)
    iterations.set(10)
    fork.set(1)
    timeUnit.set("ns")
    if (project.hasProperty("jmhInclude")) {
        includes.add(project.property("jmhInclude") as String)
    }
    if (project.hasProperty("jmhProfilers")) {
        profilers.add(project.property("jmhProfilers") as String)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "17"
}
