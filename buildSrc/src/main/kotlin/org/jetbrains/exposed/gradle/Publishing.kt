
package org.jetbrains.exposed.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom

infix fun <T> Property<T>.by(value: T) {
    set(value)
}

/**
 * Whether this project publishes a Maven artifact.
 *
 * The root build applies the Maven Publish plugin to exactly the modules that are released, so its
 * presence is the source of truth for publish state. Prefer this over a hand-maintained list of module
 * names, which silently goes stale whenever a module is added.
 */
fun Project.publishesMavenArtifact(): Boolean = plugins.hasPlugin("maven-publish")

fun MavenPom.configureMavenCentralMetadata(project: Project) {
    name by project.name
    description by "Exposed, an ORM framework for Kotlin"
    url by "https://github.com/JetBrains/Exposed"

    licenses {
        license {
            name by "The Apache Software License, Version 2.0"
            url by "https://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution by "repo"
        }
    }

    developers {
        developer {
            id by "JetBrains"
            name by "JetBrains Team"
            organization by "JetBrains"
            organizationUrl by "https://www.jetbrains.com"
        }
    }

    scm {
        url by "https://github.com/JetBrains/Exposed"
        connection by "scm:git:git://github.com/JetBrains/Exposed.git"
        developerConnection by "scm:git:git@github.com:JetBrains/Exposed.git"
    }
}
