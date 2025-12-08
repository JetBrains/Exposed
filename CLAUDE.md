# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Exposed is a lightweight ORM framework for Kotlin that provides two APIs:
- **DSL API**: Type-safe SQL-wrapping Domain Specific Language (in `exposed-core`)
  - Works with both JDBC (`exposed-jdbc`) and R2DBC (`exposed-r2dbc`)
- **DAO API**: Lightweight Data Access Object API (in `exposed-dao`)
  - **Only works with JDBC** - does not support R2DBC

## Module Architecture

### Core Modules
- **exposed-core**: Foundation layer with DSL API, database abstractions, column types, and vendor dialects
- **exposed-dao**: DAO API with entity classes and relationships (JDBC only, does not work with R2DBC)
- **exposed-jdbc**: JDBC implementation with blocking transactions
- **exposed-r2dbc**: R2DBC implementation with suspending transactions

### Extension Modules
- **exposed-java-time**, **exposed-jodatime**, **exposed-kotlin-datetime**: Date/time support
- **exposed-json**: JSON/JSONB column types
- **exposed-crypt**: Encrypted column types
- **exposed-money**: JavaMoney MonetaryAmount support
- **exposed-migration-core**: Common migration functionality
- **exposed-migration-jdbc**: JDBC-based schema migrations
- **exposed-migration-r2dbc**: R2DBC-based schema migrations
- **exposed-spring-boot-starter**: Spring Boot integration
- **spring-transaction**: Spring Framework transaction manager

### Test Modules
- **exposed-tests**: Main JDBC-based test suite
- **exposed-r2dbc-tests**: R2DBC-specific test suite
- **exposed-jdbc-r2dbc-tests**: Cross-compatibility tests

## Build & Development

### Building
```bash
./gradlew compileKotlin  # Compile the projects code
./gradlew detekt         # Validate code style
./gradlew apiDump        # Update dokka API docs after changing public API
```

### Running Tests

Tests are organized by database and dialect. Each module has database-specific test tasks:

#### Quick test with H2 (no Docker required)
```bash
./gradlew test_h2_v2                              # All modules with H2
./gradlew :exposed-tests:test_h2_v2               # JDBC Tests with H2
./gradlew :exposed-r2dbc-tests:test_h2_v2         # R2DBC Tests with H2
```

#### Test with Postgres
```bash
./gradlew test_postgres                           # All modules with Postgres
./gradlew :exposed-tests:test_postgres            # JDBC Tests with Postgres
./gradlew :exposed-r2dbc-tests:test_postgres      # R2DBC Tests with Postgres
```

#### Test with specific database (requires Docker)
```bash
# Start database containers first
./gradlew mariadbComposeUp        # Start MariaDB
./gradlew postgresComposeUp       # Start PostgreSQL
./gradlew mysql8ComposeUp         # Start MySQL 8
./gradlew oracleComposeUp         # Start Oracle
./gradlew sqlserverComposeUp      # Start SQL Server

# Run tests
./gradlew :exposed-tests:test_postgres
./gradlew :exposed-tests:test_mysql_v8
./gradlew :exposed-tests:test_mariadb

# Stop containers
./gradlew postgresComposeDownForced
```

#### Run specific test class or method with H2
```bash
./gradlew :exposed-tests:test_h2_v2 --tests "org.jetbrains.exposed.v1.tests.shared.dml.InsertTests"
./gradlew :exposed-tests:test_h2_v2 --tests "*.InsertTests.testBatchInsert"
```

#### Available test databases
- `test_h2_v2`, `test_h2_v2_mysql`, `test_h2_v2_psql`, etc. (H2 with different dialect emulations)
- `test_sqlite`
- `test_mysql_v5`, `test_mysql_v8`
- `test_mariadb`
- `test_postgres`, `test_postgresng`
- `test_oracle`
- `test_sqlserver`

## Testing Infrastructure

### Test Base Classes and Utilities

Tests inherit from different base classes depending on the driver:

**JDBC Tests** - inherit from `DatabaseTestsBase` (in `exposed-tests/src/main/kotlin/org/jetbrains/exposed/v1/tests/`):
- Tests are parameterized by database dialect using `@ParameterizedClass` and `@MethodSource("data")`
- Each test automatically runs against all enabled dialects
- Available dialects are determined by system properties set by Gradle test tasks

**R2DBC Tests** - inherit from `R2dbcDatabaseTestsBase` (in `exposed-r2dbc-tests/src/main/kotlin/`):
- Similar parameterized testing pattern as JDBC
- Uses suspending functions and coroutine context
- Test methods use `= runTest { }` for coroutine support, or utils methods like `withDb`, `withTables`,

### TestDB Enums

There are separate `TestDB` enums for JDBC and R2DBC tests:

**JDBC TestDB** (`exposed-tests/src/main/kotlin/org/jetbrains/exposed/v1/tests/TestDB.kt`):
- Connection strings using JDBC URLs (e.g., `jdbc:h2:mem:...`, `jdbc:postgresql://...`)
- JDBC driver class names
- Before/after connection hooks
- Database-specific configuration (e.g., H2 dialect emulation modes)

Available JDBC TestDB values:
- `H2_V2`, `H2_V2_MYSQL`, `H2_V2_PSQL`, `H2_V2_MARIADB`, `H2_V2_ORACLE`, `H2_V2_SQLSERVER`
- `SQLITE`, `MYSQL_V5`, `MYSQL_V8`, `MARIADB`, `POSTGRESQL`, `POSTGRESQLNG`, `ORACLE`, `SQLSERVER`

**R2DBC TestDB** (`exposed-r2dbc-tests/src/main/kotlin/org/jetbrains/exposed/v1/r2dbc/tests/TestDB.kt`):
- Connection strings using R2DBC URLs (e.g., `r2dbc:h2:mem:...`, `r2dbc:postgresql://...`)
- R2DBC isolation levels
- Suspend-aware before/after connection hooks

Available R2DBC TestDB values:
- `H2_V2`, `H2_V2_MYSQL`, `H2_V2_PSQL`, `H2_V2_MARIADB`, `H2_V2_ORACLE`, `H2_V2_SQLSERVER`
- `MYSQL_V5`, `MYSQL_V8`, `MARIADB`, `POSTGRESQL`, `ORACLE`, `SQLSERVER`
- Note: R2DBC does **not** support `SQLITE` or `POSTGRESQLNG`

### Writing Tests

Tests extend `DatabaseTestsBase` and use these helper functions:

#### JDBC Tests with `withDb`
```kotlin
class MyTests : DatabaseTestsBase() {
    @Test
    fun testSomething() {
        withDb { testDb ->  // Runs against current dialect
            // Create tables
            SchemaUtils.create(MyTable)

            // Insert/query data
            MyTable.insert { it[name] = "test" }

            // Clean up
            SchemaUtils.drop(MyTable)
        }
    }
}
```

#### Using `withTables` for automatic table management
```kotlin
@Test
fun testWithTables() {
    withTables(MyTable, AnotherTable) {
        // Tables are created before block and dropped after
        MyTable.insert { it[name] = "test" }
    }
}
```

#### Conditional tests
```kotlin
@Test
fun testPostgresOnly() {
    withDb(TestDB.POSTGRESQL) {  // Only runs for PostgreSQL
        // Postgres-specific test
    }
}
```

#### Skip databases that don't support a feature
```kotlin
@Test
fun testJsonSupport() {
    withTables(JsonTable, excludeSettings = listOf(TestDB.SQLITE, TestDB.MYSQL_V5)) {
        // Test JSON columns
    }
}
```

## Important Patterns

### Transaction Context
- JDBC: `transaction { }` - blocking transaction execution
- JDBC: `suspendTransaction { }` - suspending, with actually blocking database connections
- R2DBC: `suspendTransaction { }` - suspending, uses coroutine context
- Never mix JDBC and R2DBC transaction functions

### Database Vendor Support
Database-specific behavior is in `exposed-core/src/main/kotlin/org/jetbrains/exposed/v1/core/vendors/`:
- `H2.kt`, `MysqlDialect.kt`, `PostgreSQL.kt`, `OracleDialect.kt`, `SQLServerDialect.kt`, `SQLiteDialect.kt`, `MariaDBDialect.kt`
- Extend `DatabaseDialect` and implement `VendorDialect`
- Override `DataTypeProvider` and `FunctionProvider` for dialect-specific SQL

## Common Development Tasks

### Adding a new column type
1. Create column type class in `exposed-core` (extends `ColumnType`)
2. Add factory method to `Table` class
3. Add dialect-specific SQL type mapping in `DataTypeProvider` implementations
4. Add tests in `exposed-tests` covering multiple databases
5. Add tests in `exposed-r2dbc-tests` covering multiple databases

### Working with migrations
- Migration modules use serialization to track schema state
- JDBC migrations: `exposed-migration-jdbc` with `MigrationUtils`
- R2DBC migrations: `exposed-migration-r2dbc` with suspend support
- Both share common code from `exposed-migration-core`

## Best Practices and Gotchas

### Multi-Database Compatibility
- Always test features against multiple databases, especially H2, PostgreSQL, and MySQL
- Use dialect checks when implementing database-specific features:
  ```kotlin
  if (currentDialectTest is PostgreSQLDialect) {
      // PostgreSQL-specific code
  }
  ```
- H2 dialect emulation modes (`H2_V2_MYSQL`, `H2_V2_PSQL`, etc.) help catch compatibility issues early

### Testing Best Practices
- Extend `DatabaseTestsBase` or `R2dbcDatabaseTestsBase` for parameterized multi-database testing
- Use `Assumptions.assumeTrue()` or `excludeSettings` argument in `withTables` to skip tests for unsupported databases
- Prefer `withTables` over manual `SchemaUtils.create/drop` for cleaner tests
- Test both JDBC and R2DBC implementations when adding core features
- Use `currentDialectTest` to access current dialect in assertions

### API Compatibility
- Run `./gradlew apiCheck` before committing public API changes
- Binary compatibility is critical - breaking changes require major version bump
- Use `@InternalApi` annotation for internal implementation details
- Document breaking changes in BREAKING_CHANGES.md under "Breaking changes" section

## Code Style and Conventions

### Style Configuration
- **EditorConfig**: `.editorconfig` defines code formatting rules
  - Indent: 4 spaces
  - Max line length: 166 characters
  - Charset: UTF-8
  - End of line: LF
  - Kotlin code style: KOTLIN_OFFICIAL

- **Detekt**: Static analysis with `detekt/detekt-config.yml`
  - Max issues: 0 (all issues must be fixed)
  - Wildcard imports are allowed
  - Magic numbers allowed in named arguments and ranges
  - Run with: `./gradlew detekt`

### Naming Conventions
- Package structure uses `org.jetbrains.exposed.v1.*` namespace
- Table objects: PascalCase (e.g., `Users`, `Cities`)
- Column names: camelCase in code, snake_case in SQL
- Test classes: Suffix with `Tests` or `Test`
- Test methods: Descriptive names starting with `test`

### Common Utilities
Located in `exposed-tests/src/main/kotlin/org/jetbrains/exposed/v1/tests/`:
- `TestUtils.kt`: `currentDialectTest`, `currentDialectMetadataTest`, helper functions
- `DatabaseTestsBase.kt`: Base class for all JDBC tests
- `R2DBCDatabaseTestsBase.kt`: Base class for all R2DBC tests
- `TestDB.kt`: Database connection configurations
- `shared/Assert.kt`: Custom assertion functions
- `shared/MiscTable.kt`, `shared/ForeignKeyTables.kt`: Reusable test tables

## Sample Projects

The `samples/` directory contains reference implementations:
- **exposed-ktor**: Ktor application with JDBC
- **exposed-ktor-r2dbc**: Ktor application with R2DBC
- **exposed-migration**: Migration examples
- **exposed-spring**: Spring Boot integration examples

These demonstrate best practices for using Exposed in real applications.

## Key Files

- `buildSrc/`: Custom Gradle plugins and build configuration
- `build.gradle.kts`: Root build configuration with testDb DSL usage
- `settings.gradle.kts`: Module definitions
- `buildScripts/docker/`: Database container configurations
- `gradle.properties`: Version and build settings
- `.editorconfig`: Code formatting rules
- `detekt/detekt-config.yml`: Static analysis configuration
