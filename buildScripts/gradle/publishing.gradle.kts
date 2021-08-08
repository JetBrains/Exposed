import org.jetbrains.exposed.gradle.*

apply(plugin = "java-library")
apply(plugin = "maven-publish")
apply(plugin = "signing")

_java {
    withJavadocJar()
    withSourcesJar()
}

_publishing {
    publications {
        create<MavenPublication>("ExposedJars") {
            artifactId = project.name
            from(project.components["java"])
            pom {
                configureMavenCentralMetadata(project)
            }
            signPublicationIfKeyPresent(project)
        }
    }
}
