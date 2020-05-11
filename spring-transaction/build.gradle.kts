import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.Versions
import tanvd.kosogor.proxy.publishJar

plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://dl.bintray.com/jfrog/jfrog-jars")
}

dependencies {
    api(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    api("org.springframework", "spring-jdbc", Versions.springFramework)
    api("org.springframework", "spring-context", Versions.springFramework)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotlinCoroutines)

    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx","kotlinx-coroutines-debug", Versions.kotlinCoroutines)
    testImplementation("org.springframework", "spring-test", Versions.springFramework)
    testImplementation("org.slf4j", "slf4j-log4j12", "1.7.26")
    testImplementation("log4j", "log4j", "1.2.17")
    testImplementation("junit", "junit", "4.12")
    testImplementation("org.hamcrest", "hamcrest-library", "1.3")
    testImplementation("com.h2database", "h2", Versions.h2)
}

publishJar {
    publication {
        artifactId = "spring-transaction"
    }

    bintray {
        username = project.properties["bintrayUser"]?.toString() ?: System.getenv("BINTRAY_USER")
        secretKey = project.properties["bintrayApiKey"]?.toString() ?: System.getenv("BINTRAY_API_KEY")
        repository = "exposed"
        info {
            publish = false
            githubRepo = "https://github.com/JetBrains/Exposed.git"
            vcsUrl = "https://github.com/JetBrains/Exposed.git"
            userOrg = "kotlin"
            license = "Apache-2.0"
        }
    }
}

tasks.withType(Test::class.java) {
    jvmArgs = listOf("-XX:MaxPermSize=256m")
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}
