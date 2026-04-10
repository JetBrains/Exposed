# Exposed Gradle plugin

This plugin simplifies the use of Exposed in applications and currently provides the following capabilities:

- Generating SQL migration scripts

## Install the plugin

To install the plugin, add it to the `plugins` block of your build script:

```kotlin
plugins {
    id("org.jetbrains.exposed.plugin") version "1.2.0"
}
```

### Requirements

- Kotlin version 2.1.+
- Gradle 8.13+
- JVM 11+

## Generate migration scripts

To generate migration scripts based on the difference between your existing database schema and your Exposed table definitions,
use the `generateMigrations` task:

```bash
./gradlew generateMigrations
```

After the task is executed, you should see the new file(s) in the directory specified on plugin configuration.

You can also integrate this task into your build process:

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

At minimum, plugin configuration must include the following elements:

- `tablesPackage`: Package name where Exposed table definitions are located.
- And either configuration for a database or TestContainers

```kotlin
// build.gradle.kts

// using a database
exposed {
    migrations {
        tablesPackage.set("com.example.db.tables")
        databaseUrl.set("jdbc:postgresql://localhost:5432/mydb")
        databaseUser.set("postgres")
        databasePassword.set("password")
    }
}

// using TestContainers
exposed {
    migrations {
        tablesPackage.set("com.example.db.tables")
        testContainersImageName.set("postgres:latest")
    }
}
```

**Note:** If the property `testContainersImageName` is set, it will override any set `database` properties
and TestContainers will be used for the script generation task.

You can optionally configure the following elements for more control over the process and generated scripts:

- `classpath`: Classpath scanned for Exposed table definitions. Defaults to the project's runtime classpath.
- `fileDirectory`: Directory where the generated migration scripts will be stored. Defaults to "src/main/resources/db/migration".
- `filePrefix`: Prefix for migration script names. Defaults to "V".
- `fileSeparator`: Separator for migration script names. Defaults to "__".
- `useUpperCaseDescription`: Whether the descriptive part of migration script names should be all in upper-case. Defaults to true.
- `fileExtension`: File extension for migration scripts. Defaults to ".sql".

```kotlin
// build.gradle.kts

exposed {
    migrations {
        // mandatory configurations ...

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

`<prefix><version><separator><description>.<extension>`

By default, this plugin generates scripts that follow Flyway's naming conventions with support for three version formats:

1. `VX__description.sql` (e.g., `V1__create_users_table.sql`)
2. `VX_Y__description.sql` (e.g., `V1_0__create_users_table.sql`)
3. `VX_YYYYMMDDHHMMSS__description.sql` (e.g., `V1_20250409163303__create_users_table.sql`)

The format used depends on any existing migration scripts detected in the project, with `VX__description.sql` being used
as the default fallback if no matching scripts are found.
