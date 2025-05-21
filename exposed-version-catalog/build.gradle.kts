plugins {
    `version-catalog`
    `maven-publish`
    signing
}

group = "org.jetbrains.exposed"

catalog {
    versionCatalog {
        val version: String by rootProject

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

publishing {
    val version: String by rootProject

    publications {
        create<MavenPublication>("versionCatalog") {
            groupId = "org.jetbrains.exposed"
            artifactId = project.name
            this.version = version
            from(components["versionCatalog"])

            pom {
                name.set(project.name)
                description.set("Exposed, an ORM framework for Kotlin - Version Catalog")
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
                    developerConnection.set("scm:git:git@github.com:JetBrains/Exposed.git")
                }
            }

            val keyId = System.getenv("exposed.sign.key.id")
            val signingKey = System.getenv("exposed.sign.key.private")
            val signingKeyPassphrase = System.getenv("exposed.sign.passphrase")
            if (!signingKey.isNullOrBlank()) {
                project.extensions.configure<SigningExtension>("signing") {
                    useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)
                    sign(this@create)
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
