plugins {
    kotlin("jvm") version "1.4.31" apply true
    id("io.github.gradle-nexus.publish-plugin") apply true
}

allprojects {
    if (this.name != "exposed-tests" && this != rootProject) {
        apply(from = rootProject.file("buildScripts/gradle/publishing.gradle.kts"))
    }
}

nexusPublishing {
    repositories {
        sonatype {
            username.set(System.getenv("exposed.sonatype.user"))
            password.set(System.getenv("exposed.sonatype.password"))
            useStaging.set(true)
        }
    }
}

repositories {
    mavenCentral()
}
