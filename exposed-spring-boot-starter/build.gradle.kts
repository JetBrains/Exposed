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
    api(project(":spring-transaction"))
    api("org.springframework.boot", "spring-boot-starter-data-jdbc", "2.1.6.RELEASE")
    api("org.springframework.boot", "spring-boot-autoconfigure", "2.1.6.RELEASE")
    compileOnly("org.springframework.boot", "spring-boot-configuration-processor", "2.1.6.RELEASE")

    testImplementation("org.springframework.boot", "spring-boot-starter-test", "2.1.6.RELEASE")
    testImplementation("com.h2database", "h2", "1.4.199")
}

publishJar {
    publication {
        artifactId = "exposed-spring-boot-starter"
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
