plugins {
    `version-catalog`
    alias(libs.plugins.maven.publish)
}

group = "org.jetbrains.exposed"

val excludedFromCatalog = setOf(
    "exposed-tests",
    "exposed-r2dbc-tests",
    "exposed-jdbc-r2dbc-tests",
    "exposed-dao-r2dbc-tests",
    "exposed-version-catalog",
)

catalog {
    versionCatalog {
        version("exposed", project.version.toString())
        rootProject.subprojects
            .map { it.name }
            .filter { it !in excludedFromCatalog }
            .sorted()
            .forEach { moduleName ->
                val alias = moduleName.removePrefix("exposed-")
                library(alias, "org.jetbrains.exposed", moduleName).versionRef("exposed")
            }
    }
}
