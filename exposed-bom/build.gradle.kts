import org.jetbrains.exposed.gradle.configureMavenCentralMetadata
import org.jetbrains.exposed.gradle.signPublicationIfKeyPresent

plugins {
    `java-platform`
    `maven-publish`
    signing
}

group = "org.jetbrains.exposed"

// This is needed as the api dependency constraints cause dependencies
javaPlatform.allowDependencies()

dependencies {
    constraints {
        rootProject.subprojects.forEach {
            if (it.plugins.hasPlugin("maven-publish") && it.name != name) {
                it.publishing.publications.all {
                    if (this is MavenPublication) {
                        if (!artifactId.endsWith("-metadata") &&
                            !artifactId.endsWith("-kotlinMultiplatform")
                        ) {
                            api(project(":${it.name}"))
                        }
                    }
                }
            }
        }
    }
}

publishing {
    val version: String by rootProject

    publications {
        create<MavenPublication>("bom") {
            groupId = "org.jetbrains.exposed"
            artifactId = project.name
            this.version = version
            from(components.getByName("javaPlatform"))
            pom {
                configureMavenCentralMetadata(project)
            }
            signPublicationIfKeyPresent(project)
        }
    }

    val publishingUsername: String? = System.getenv("PUBLISHING_USERNAME")
    val publishingPassword: String? = System.getenv("PUBLISHING_PASSWORD")

    repositories {
        maven {
            name = "Exposed"
            url = uri("https://maven.pkg.jetbrains.space/public/p/exposed/release")
            credentials {
                username = publishingUsername
                password = publishingPassword
            }
        }
    }
}
