@file:Suppress("UnstableApiUsage")

package org.jetbrains.exposed.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import org.gradle.api.plugins.JavaPluginExtension


fun MavenPom.setPomMetadata(project: Project) {
    name.set(project.name)
    description.set("${project.name}; an ORM framework for Kotlin")
    url.set("https://github.com/tpasipanodya/Exposed")

    developers {
        developer {
            id.set("Tafadzwa Pasipanodya")
            name.set("Tafadzwa Pasipanodya")
            email.set("tmpasipanodya@gmail.com")
        }
        developer {
            id.set("JetBrains")
            name.set("JetBrains Team")
            organization.set("JetBrains")
            organizationUrl.set("https://www.jetbrains.com")
        }
    }

    scm {
        connection.set("scm:git:git://github.com/tpasipanodya/Exposed.git")
        developerConnection.set("scm:git:ssh://github.com/tpasipanodya/Exposed.git")
        url.set("http://github.com/tpasipanodya/Exposed/tree/main")
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

fun isReleaseBuild() = System.getenv("IS_RELEASE_BUILD")?.toBoolean() == true
