# Exposed Gradle plugin

The Exposed Gradle plugin provides build-time tooling for working with Exposed-based database schemas.

Its primary functionality is the [generation of SQL migration scripts](#generate-migration-scripts) by comparing Exposed
table definitions with an existing database schema.

## Requirements

* Kotlin 2.1+
* Gradle 8.13+ ([Gradle installation guide](https://docs.gradle.org/current/userguide/installation.html))
* JVM 11+

## Installation

To install the plugin, add it to the `plugins` block of your Gradle build script:

```kotlin
plugins {
  id("org.jetbrains.exposed.plugin") version "%exposed_version%"
}
```

## Generate migration scripts

To generate migration scripts based on the difference between your existing database schema and your Exposed table definitions,
use the `generateMigrations` task:

```bash
./gradlew generateMigrations
```

Generated files are written to the configured output directory.

### Integration with build lifecycle

You can also integrate the task into your Gradle build process:

```kotlin
// Generate migration scripts before the build task
tasks.named("build") {
    dependsOn("generateMigrations")
}

// Generate migration scripts before the processResources task
tasks.named("processResources") {
    dependsOn("generateMigrations")
}
```

### Configuration

Configure the plugin using the `exposed.migrations` block in your `build.gradle.kts` file, including:

* `tablesPackage`: package name where Exposed table definitions are located.
* either a database configuration or a `TestContainers` configuration.

```kotlin
// Using a database
exposed {
    migrations {
        tablesPackage.set("com.example.db.tables")
        databaseUrl.set("jdbc:postgresql://localhost:5432/mydb")
        databaseUser.set("postgres")
        databasePassword.set("password")
    }
}

// Using TestContainers
exposed {
    migrations {
        tablesPackage.set("com.example.db.tables")
        testContainersImageName.set("postgres:latest")
    }
}
```

> When `testContainersImageName` is set, `TestContainers` is used for schema generation instead of a direct database
> connection.
>
{style="note"}

### Additional configuration

Optionally, you can configure the following properties for more control over the process and generated scripts:

<deflist type="medium">
<def>
<title><code>classpath</code></title>
Classpath scanned for Exposed table definitions.<br/>
Defaults to the project's runtime classpath.
</def>
<def>
<title><code>fileDirectory</code></title>

Directory where the generated migration scripts will be stored.<br/>
Defaults to `"src/main/resources/db/migration"`.

</def>
<def>
<title><code>filePrefix</code></title>

Prefix for migration script names.<br/>
Defaults to `"V"`.
</def>
<def>
<title><code>fileSeparator</code></title>

Separator for migration script names.<br/>
Defaults to `"__"`.
</def>
<def>
<title><code>useUpperCaseDescription</code></title>

Whether the descriptive part of migration script names should be all in upper-case.<br/>
Defaults to `true`.
</def>
<def>
<title><code>fileExtension</code></title>

File extension for migration scripts.<br/>
Defaults to `".sql"`.
</def>
</deflist>

```kotlin
exposed {
    migrations {
        // ...
        classpath = sourceSets.main.get().runtimeClasspath
        fileDirectory.set(layout.projectDirectory.dir("src/main/resources/db/migration"))
        filePrefix.set("V")
        fileSeparator.set("__")
        useUpperCaseDescription.set(true)
        fileExtension.set(".sql")
    }
}
```

### File naming

The default migration script naming pattern is as follows:

```text
<prefix><version><separator><description>.<extension>
```

The plugin generates migration scripts compatible with Flyway-style naming conventions and supports the following
version formats:
1. `V<version>__description.sql` (e.g., `V1__create_users_table.sql`)
2. `V<major>_<minor>__description.sql` (e.g., `V1_0__create_users_table.sql`)
3. `V<major>_<timestamp>__description.sql` (e.g., `V1_20250409163303__create_users_table.sql`)

The selected format depends on existing migration files in the project. If no matching files are found, the default 
format `V<version>__description.sql` is used.


