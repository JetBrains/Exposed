package org.jetbrains.exposed.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

infix fun <T> Property<T>.by(value: T) {
    set(value)
}

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

fun MavenPublication.signPublicationIfKeyPresent(project: Project) {
    val keyId = System.getenv("exposed.sign.key.id")
    val signingKey = System.getenv("exposed.sign.key.private")
    val signingKeyPassphrase = System.getenv("exposed.sign.passphrase")
    if (!signingKey.isNullOrBlank()) {
        project.extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, signingKey.replace(" ", "\r\n"), signingKeyPassphrase)
            sign(this@signPublicationIfKeyPresent)
        }
    }
}

@Suppress("FunctionNaming")
fun Project._publishing(configure: PublishingExtension.() -> Unit) {
    extensions.configure("publishing", configure)
}

@Suppress("FunctionNaming")
fun Project._java(configure: JavaPluginExtension.() -> Unit) {
    extensions.configure("java", configure)
}
