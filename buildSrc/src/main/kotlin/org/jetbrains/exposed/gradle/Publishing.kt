package org.jetbrains.exposed.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.provideDelegate
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
            useInMemoryPgpKeys(keyId, preprocessPrivateGpgKey(signingKey), signingKeyPassphrase)
            sign(this@signPublicationIfKeyPresent)
        }
    }
}

fun Project.publishing(configure: PublishingExtension.() -> Unit) {
    extensions.configure("publishing", configure)
}

fun Project.java(configure: JavaPluginExtension.() -> Unit) {
    extensions.configure("java", configure)
}

fun Project.configurePublishing() {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    java {
        withJavadocJar()
        withSourcesJar()
    }

    val version: String by rootProject

    publishing {
        publications {
            create<MavenPublication>("exposed") {
                groupId = "org.jetbrains.exposed"
                artifactId = project.name

                setVersion(version)

                from(components["java"])
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
}

private fun preprocessPrivateGpgKey(key: String): String {
    val prefix = "-----BEGIN PGP PRIVATE KEY BLOCK-----"
    val suffix = "-----END PGP PRIVATE KEY BLOCK-----"
    val delimiter = "\r\n"
    return prefix + delimiter + key
        .replace(prefix, "")
        .replace(suffix, "")
        .replace(" ", "\r\n") + delimiter + suffix
}
