import org.jetbrains.exposed.gradle.*

apply(plugin = "java-library")
apply(plugin = "maven-publish")
apply(plugin = "signing")

_java {
    withJavadocJar()
    withSourcesJar()
}

val version: String by rootProject

_publishing {
    publications {
        create<MavenPublication>("exposed") {
            groupId = "org.jetbrains.exposed"
            artifactId = project.name
            version = version
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
