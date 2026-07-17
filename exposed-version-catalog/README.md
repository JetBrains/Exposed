# exposed-version-catalog

A [Gradle version catalog](https://docs.gradle.org/current/userguide/platforms.html#sec:sharing-catalogs)
for all published Exposed modules. It lets you reference Exposed dependencies through type-safe
accessors instead of hard-coding coordinates and versions.

## Usage

Import the catalog in your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("exposedLibs") {
            from("org.jetbrains.exposed:exposed-version-catalog:1.3.2")
        }
    }
}
```

Then reference modules in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(exposedLibs.core)
    implementation(exposedLibs.jdbc)
    implementation(exposedLibs.kotlin.datetime)
}
```

The accessor for each module is its module name with the `exposed-` prefix removed and dashes turned
into nested accessors, for example:

| Module                    | Accessor                        |
|---------------------------|---------------------------------|
| `exposed-core`            | `exposedLibs.core`              |
| `exposed-jdbc`            | `exposedLibs.jdbc`              |
| `exposed-r2dbc`           | `exposedLibs.r2dbc`             |
| `exposed-kotlin-datetime` | `exposedLibs.kotlin.datetime`   |
| `spring-transaction`      | `exposedLibs.spring.transaction` |

All libraries share the `exposed` version, so you can override the whole Exposed version in one place:

```kotlin
versionCatalogs {
    create("exposedLibs") {
        from("org.jetbrains.exposed:exposed-version-catalog:1.3.2")
        version("exposed", "1.3.2")
    }
}
```

> **Why `exposedLibs` and not `exposed`?**
> The [Exposed Gradle plugin](../exposed-gradle-plugin) registers a project extension named `exposed`
> (the `exposed { migrations { } }` DSL). A version catalog is also exposed as a project extension named
> after the catalog, so naming this catalog `exposed` would clash with the plugin
> (`Cannot add extension with name 'exposed'`). Naming it `exposedLibs` keeps both usable in the same
> project. If you don't apply the Exposed Gradle plugin, you're free to name the catalog `exposed`.
