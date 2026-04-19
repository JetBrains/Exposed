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
- `fileVersionFormat`: Version format for migration script names. Defaults to using the full current timestamp (with seconds) in the format YYYYMMDDHHMMSS.
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
        fileVersionFormat.set(VersionFormat.TIMESTAMP_ONLY)
        fileSeparator.set("__")
        useUpperCaseDescription.set(true)
        fileExtension.set(".sql")
    }
}
```

### File naming

The default migration script naming pattern is as follows:

`<prefix><version><separator><description><extension>`

By default, this plugin generates scripts that use timestamps as part of the naming convention,
with support for the following `VersionFormat` options:

- `TIMESTAMP_ONLY` (e.g., `V20260417195521__create_table_users.sql`)
- `TIMESTAMP_WITHOUT_SECONDS` (e.g., `V202604171955__create_table_users.sql`)
- `MAJOR_TIMESTAMP` (e.g., `V3_20260417195521__create_table_users.sql`)
- `MAJOR_TIMESTAMP_WITHOUT_SECONDS` (e.g., `V3_202604171955__create_table_users.sql`)
- `MAJOR_MINOR` (e.g., `V3_1__create_table_users.sql`)
- `MAJOR_ONLY` (e.g., `V3__create_table_users.sql`)

The default setting can be configured by passing a specific `VersionFormat` to `fileVersionFormat` in your `build.gradle.kts`.

If you choose to use a format that relies on a major version, the specified `fileDirectory` will be searched for existing
scripts from which the next highest version will be resolved. If the directory is empty or no script files of the above
formats are detected, the generated major version will start at 1.

The file naming configurations specified in your `build.gradle.kts` can be ignored when the `generateMigrations` task is
run by passing the exact required filename to use as a command line argument:

```bash
./gradlew generateMigrations --filename=V0__initialize_schema.sql
```

**Note:** If a filename argument is passed, only a single migration script will be generated, which will contain all the
necessary migration statements. This will happen even if multiple Exposed table definitions are involved in the schema diff,
which would otherwise generate a new migration script per table.
