import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import tanvd.kosogor.proxy.publishJar

plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
    maven("https://dl.bintray.com/jfrog/jfrog-jars")
}

dependencies {
    api(project(":exposed"))
    api("org.springframework", "spring-jdbc", "5.1.1.RELEASE")
    api("org.springframework", "spring-context", "5.1.1.RELEASE")
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.0.1")
    implementation("com.h2database", "h2", "1.4.197")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.springframework", "spring-test", "5.1.1.RELEASE")
    testImplementation("org.slf4j", "slf4j-log4j12", "1.7.25")
    testImplementation("log4j", "log4j", "1.2.17")
    testImplementation("junit", "junit", "4.12")
    testImplementation("org.hamcrest", "hamcrest-library", "1.3")
    testImplementation("com.h2database", "h2", "1.4.197")
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
