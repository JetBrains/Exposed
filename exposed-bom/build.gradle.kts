import org.jetbrains.exposed.gradle.publishesMavenArtifact

plugins {
    `java-platform`
    alias(libs.plugins.maven.publish)
}

group = "org.jetbrains.exposed"

javaPlatform.allowDependencies()

dependencies {
    constraints {
        rootProject.subprojects.forEach {
            if (it.publishesMavenArtifact() && it.name != name && it.name != "exposed-version-catalog") {
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
