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

val SPRING_BOOT_VERSION = "2.2.0.RELEASE"

dependencies {
    api(project(":exposed-core"))
    api(project(":exposed-dao"))
    api(project(":spring-transaction"))
    api("org.springframework.boot", "spring-boot-starter-data-jdbc", SPRING_BOOT_VERSION)
    api("org.springframework.boot", "spring-boot-autoconfigure", SPRING_BOOT_VERSION)
    compileOnly("org.springframework.boot", "spring-boot-configuration-processor", SPRING_BOOT_VERSION)

    testImplementation("org.springframework.boot", "spring-boot-starter-test", SPRING_BOOT_VERSION)
    testImplementation("org.springframework.boot", "spring-boot-starter-webflux", SPRING_BOOT_VERSION) // put in testImplementation so no hard dependency for those using the starter
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
