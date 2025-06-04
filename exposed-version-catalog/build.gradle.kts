plugins {
    `version-catalog`
    `maven-publish`
    signing
}

group = "org.jetbrains.exposed"

catalog {
    versionCatalog {
        create("exposed") {
            rootProject.subprojects.filter { 
                it.name != "exposed-version-catalog" && 
                it.name != "exposed-tests" && 
                it.name != "exposed-r2dbc-tests" && 
                it.name != "exposed-bom" 
            }.forEach { project ->
                val moduleName = project.name.removePrefix("exposed-")
                val nameParts = moduleName.split("-")
                val accessorName = if (nameParts.size > 1) {
                    nameParts.joinToString(".") { it }
                } else {
                    moduleName
                }

                val version: String by rootProject
                val artifactId = project.name
                library(accessorName, "org.jetbrains.exposed:$artifactId:$version")
            }
        }
    }
}

publishing {
    val version: String by rootProject

    publications {
        create<MavenPublication>("versionCatalog") {
            groupId = "org.jetbrains.exposed"
            artifactId = project.name
            this.version = version
            from(components["versionCatalog"])

            pom {
                name.set("Exposed Version Catalog")
                description.set("Version Catalog for Exposed modules")
                url.set("https://github.com/JetBrains/Exposed")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("JetBrains")
                        name.set("JetBrains Team")
                        organization.set("JetBrains")
                        organizationUrl.set("https://www.jetbrains.com")
                    }
                }

                scm {
                    url.set("https://github.com/JetBrains/Exposed")
                    connection.set("scm:git:git://github.com/JetBrains/Exposed.git")
                }
            }
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

// Sign publications if signing key is present
val signingKey: String? = System.getenv("SIGN_KEY_PRIVATE")
val signingKeyPassphrase: String? = System.getenv("SIGN_KEY_PASSPHRASE")

if (signingKey != null && signingKeyPassphrase != null) {
    signing {
        useInMemoryPgpKeys(signingKey, signingKeyPassphrase)
        sign(publishing.publications["versionCatalog"])
    }
}
