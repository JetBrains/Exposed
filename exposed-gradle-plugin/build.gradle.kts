import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    kotlin("jvm")

    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(gradleApi())

    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-migration-jdbc"))

    implementation(libs.kotlin.stdlib)

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
    implementation(libs.mysql)
    implementation(libs.postgre)
    implementation(libs.mariadb)
    implementation(libs.oracle)
    implementation(libs.mssql)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
    website = "https://www.jetbrains.com/exposed/"
    vcsUrl = "https://github.com/JetBrains/Exposed"

    plugins {
        create("exposed").apply {
            id = "org.jetbrains.exposed.plugin"
            displayName = "Exposed Gradle Plugin"
            implementationClass = "org.jetbrains.exposed.v1.gradle.plugin.ExposedGradlePlugin"
            description = "Exposed Gradle Plugin configures the generation of migration scripts for applications that use Exposed"
            tags = setOf("exposed", "kotlin", "sql", "database", "orm")
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "11"
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.VERSION_1_8 > JavaVersion.current()) {
        jvmArgs("-XX:MaxPermSize=256m")
    }

    // Needed for JDK 17 + ProjectBuilder; otherwise first test always fails with 'Could not inject synthetic classes'
    // https://github.com/gradle/gradle/issues/18647
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED")

    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }

    useJUnitPlatform()
}
