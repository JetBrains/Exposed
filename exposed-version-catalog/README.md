# Exposed Version Catalog

This module provides a [Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog) for Exposed modules.

## Usage

### In settings.gradle.kts

Add the following to your `settings.gradle.kts` file:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("exposed") {
            from("org.jetbrains.exposed:exposed-version-catalog:<version>")
        }
    }
}
```

Replace `<version>` with the version of Exposed you want to use.

### In build.gradle.kts

You can then use the version catalog in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(exposed.core)
    implementation(exposed.dao)
    implementation(exposed.jdbc)
    
    // For modules with hyphens in their names, use dot notation
    implementation(exposed.kotlin.datetime)
    implementation(exposed.java.time)
    implementation(exposed.spring.boot.starter)
}
```

## Available Dependencies

The version catalog includes all Exposed modules:

- `exposed.core` - Core module with SQL DSL
- `exposed.dao` - DAO API implementation
- `exposed.jdbc` - JDBC transport implementation
- `exposed.kotlin.datetime` - Kotlin DateTime extensions
- `exposed.java.time` - Java Time extensions
- `exposed.jodatime` - JodaTime extensions
- `exposed.json` - JSON data type extensions
- `exposed.money` - MonetaryAmount extensions
- `exposed.crypt` - Encrypted data type extensions
- `exposed.migration` - Database migration support
- `exposed.r2dbc` - R2DBC reactive implementation
- `exposed.spring.boot.starter` - Spring Boot integration
- `spring.transaction` - Spring transaction support

## Benefits

Using the version catalog provides several benefits:

1. **Type-safe accessors** - You get type-safe accessors for all Exposed modules
2. **Version consistency** - All modules use the same version
3. **Simplified dependency management** - No need to specify group and version for each dependency
4. **IDE support** - Better IDE support with code completion
