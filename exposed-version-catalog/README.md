# Exposed Version Catalog

This module provides a Gradle Version Catalog for all Exposed dependencies.

## Usage

Add the version catalog to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("exposed") {
            from("org.jetbrains.exposed:exposed-version-catalog:1.0.0-rc-2")
        }
    }
}
```

Then use it in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(exposed.jdbc)
    implementation(exposed.kotlin.datetime)
    implementation(exposed.dao)
}
```

## Available Dependencies

### Core Libraries
- `exposed.core` - Core functionality
- `exposed.dao` - DAO support
- `exposed.jdbc` - JDBC support

### Date/Time Libraries
- `exposed.jodatime` - Joda-Time support
- `exposed.java.time` - Java Time API support
- `exposed.kotlin.datetime` - Kotlin DateTime support

### Additional Features
- `exposed.json` - JSON support
- `exposed.crypt` - Cryptography support
- `exposed.money` - Money/Currency support

### R2DBC
- `exposed.r2dbc` - R2DBC support

### Migration
- `exposed.migration.core` - Migration core functionality
- `exposed.migration.jdbc` - JDBC migration support
- `exposed.migration.r2dbc` - R2DBC migration support

### Spring Integration
- `exposed.spring.boot.starter` - Spring Boot starter
- `exposed.spring.transaction` - Spring transaction support

### BOM
- `exposed.bom` - Bill of Materials for dependency management
