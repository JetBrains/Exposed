import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import tanvd.kosogor.proxy.publishJar

plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://dl.bintray.com/jfrog/jfrog-jars")
}

val SPRING_FRAMEWORK_VERSION = "5.2.0.RELEASE"

dependencies {
    api(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-dao"))
    api("org.springframework", "spring-jdbc", SPRING_FRAMEWORK_VERSION)
    api("org.springframework", "spring-context", SPRING_FRAMEWORK_VERSION)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.3")

    testImplementation(project(":exposed-dao"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.springframework", "spring-test", SPRING_FRAMEWORK_VERSION)
    testImplementation("org.slf4j", "slf4j-log4j12", "1.7.26")
    testImplementation("log4j", "log4j", "1.2.17")
    testImplementation("junit", "junit", "4.12")
    testImplementation("org.hamcrest", "hamcrest-library", "1.3")
    testImplementation("com.h2database", "h2", "1.4.199")
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
