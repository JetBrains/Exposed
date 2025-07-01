package org.jetbrains.exposed.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.catalog.CatalogPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register

class VersionCatalogPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply(plugin = "version-catalog")
        project.apply(plugin = "maven-publish")
        project.apply(plugin = "signing")

        project.group = "org.jetbrains.exposed"

        project.configure<CatalogPluginExtension> {
            versionCatalog {
                val version: String by project.rootProject

                // Core modules
                library("core", "org.jetbrains.exposed:exposed-core:$version")
                library("dao", "org.jetbrains.exposed:exposed-dao:$version")
                library("jdbc", "org.jetbrains.exposed:exposed-jdbc:$version")

                // Extension modules
                library("java.time", "org.jetbrains.exposed:exposed-java-time:$version")
                library("jodatime", "org.jetbrains.exposed:exposed-jodatime:$version")
                library("kotlin.datetime", "org.jetbrains.exposed:exposed-kotlin-datetime:$version")
                library("json", "org.jetbrains.exposed:exposed-json:$version")
                library("money", "org.jetbrains.exposed:exposed-money:$version")
                library("crypt", "org.jetbrains.exposed:exposed-crypt:$version")
                library("migration", "org.jetbrains.exposed:exposed-migration:$version")

                // Integration modules
                library("spring.boot.starter", "org.jetbrains.exposed:exposed-spring-boot-starter:$version")
                library("spring.transaction", "org.jetbrains.exposed:spring-transaction:$version")

                // R2DBC module
                library("r2dbc", "org.jetbrains.exposed:exposed-r2dbc:$version")

                // BOM
                library("bom", "org.jetbrains.exposed:exposed-bom:$version")

                // Create bundles
                bundle("core", listOf("core", "dao", "jdbc"))
                bundle("datetime", listOf("java.time", "jodatime", "kotlin.datetime"))
            }
        }

        project.extensions.configure<PublishingExtension>("publishing") {
            val version: String by project.rootProject

            publications {
                create<MavenPublication>("versionCatalog") {
                    groupId = "org.jetbrains.exposed"
                    artifactId = project.name
                    this.version = version
                    from(project.components["versionCatalog"])

                    pom {
                        configureMavenCentralMetadata(project)
                        description by "Exposed, an ORM framework for Kotlin - Version Catalog"
                    }
                    signPublicationIfKeyPresent(project)
                }
            }

            val publishingUsername: String? = System.getenv("PUBLISHING_USERNAME")
            val publishingPassword: String? = System.getenv("PUBLISHING_PASSWORD")

            repositories {
                maven {
                    name = "Exposed"
                    url = project.uri("https://maven.pkg.jetbrains.space/public/p/exposed/release")
                    credentials {
                        username = publishingUsername
                        password = publishingPassword
                    }
                }
            }
        }
    }
}
