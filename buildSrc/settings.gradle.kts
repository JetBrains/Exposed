dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))

            // KUP: Make it possible to override Kotlin version
            val kotlinVersion = providers.gradleProperty("kotlin_version").orNull
            if (kotlinVersion != null) version("kotlin", kotlinVersion)
        }
    }

    repositories {
        //KUP: override kotlin repo url
        val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
        val kotlin_version: String? by settings
        val repoUrl = kotlinRepoUrl
        if (!repoUrl.isNullOrEmpty() && !kotlin_version.isNullOrEmpty()) {
            exclusiveContent {
                forRepository {
                    maven(repoUrl)
                }
                filter {
                    includeVersionByRegex("org\\.jetbrains\\.kotlin.*", ".*", kotlin_version)
                }
            }
        }
    }
}

