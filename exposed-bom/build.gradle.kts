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
    publications {
        create<MavenPublication>("bom") {
            from(components.getByName("javaPlatform"))
            pom {
                configureMavenCentralMetadata(project)
            }
            signPublicationIfKeyPresent(project)
        }
    }
}
