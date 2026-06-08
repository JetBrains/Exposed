import org.jetbrains.exposed.gradle.publishesMavenArtifact

plugins {
    `version-catalog`
    alias(libs.plugins.maven.publish)
}

group = "org.jetbrains.exposed"

catalog {
    versionCatalog {
        version("exposed", project.version.toString())
        rootProject.subprojects
            .filter { it.name != project.name && it.publishesMavenArtifact() }
            .map { it.name }
            .sorted()
            .forEach { moduleName ->
                val alias = moduleName.removePrefix("exposed-")
                library(alias, "org.jetbrains.exposed", moduleName).versionRef("exposed")
            }
    }
}
