# Exposed Project Guidelines

## Project Overview
Exposed is an ORM framework for Kotlin, developed by JetBrains. It provides SQL database access with two approaches:
1. **SQL DSL (Domain Specific Language)** - A typesafe SQL wrapping that allows writing SQL queries in Kotlin code
2. **DAO (Data Access Objects)** - A lightweight object-relational mapping layer

The project's mascot is the cuttlefish, which represents Exposed's ability to adapt to different database engines, allowing developers to build applications without dependencies on specific database engines.

## Project Structure
Exposed is organized into multiple modules:

### Core Modules
- **exposed-core**: Base module containing both DSL API and mapping functionality
- **exposed-dao**: DAO API implementation
- **exposed-jdbc**: Transport level implementation based on Java JDBC API
- **exposed-r2dbc**: Reactive implementation based on R2DBC

### Extension Modules
- **exposed-java-time**: Date-time extensions based on Java 8 Time API
- **exposed-jodatime**: Date-time extensions based on JodaTime library
- **exposed-kotlin-datetime**: Date-time extensions based on kotlinx-datetime
- **exposed-json**: JSON and JSONB data type extensions
- **exposed-money**: Extensions to support MonetaryAmount from "javax.money:money-api"
- **exposed-crypt**: Column types for storing encrypted data in DB with client-side encoding/decoding

### Integration Modules
- **exposed-spring-boot-starter**: Spring Boot integration
- **spring-transaction**: Spring transaction support

### Testing Modules
- **exposed-tests**: Tests for JDBC implementation
- **exposed-r2dbc-tests**: Tests for R2DBC implementation
- **exposed-jdbc-r2dbc-tests**: Tests for JDBC and R2DBC mixed in one module

## Supported Databases
Exposed supports multiple database engines:
- H2 (versions 2.x; 1.x version is deprecated)
- MariaDB
- MySQL
- Oracle
- PostgreSQL (including PostgreSQL using the pgjdbc-ng JDBC driver)
- Microsoft SQL Server
- SQLite

## Testing Guidelines
Tests in Exposed are organized by database type. The project has a comprehensive test suite that runs against multiple database backends to ensure compatibility.

### Test Structure
- Tests are organized in packages by database type (h2, mysql, postgresql, sqlite)
- Common test code is in the "shared" directory
- Demo/example tests are in the "demo" directory

### Running Tests
Tests can be run against different database backends. The build system is configured to support testing against:
- H2 (versions 1 and 2)
- SQLite
- MySQL (versions 5 and 8)
- MariaDB (versions 2 and 3)
- Oracle
- PostgreSQL (including with pgjdbc-ng driver)
- SQL Server

When making changes to the codebase, you should run tests against the relevant database backends to ensure compatibility.

## Build Instructions
The project uses Gradle for building. Key build commands:

- `./gradlew build`: Build the entire project
- `./gradlew test`: Run all tests
- `./gradlew <module>:test`: Run tests for a specific module
- `./gradlew <module>:test --tests "org.jetbrains.exposed.v1.sql.tests.<TestClass>"`: Run a specific test class

## Code Style Guidelines
The project uses detekt for static code analysis. Code should follow Kotlin coding conventions and pass detekt checks.

Key style points:
- Follow Kotlin coding conventions
- Use meaningful names for classes, methods, and variables
- Write comprehensive tests for new functionality
- Maintain backward compatibility when possible

## Contributing
When contributing to Exposed:
1. Link your work to an existing issue on [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED)
2. Ensure your changes pass all tests against relevant database backends
3. Follow the code style guidelines
4. Write tests for new functionality
5. Update documentation as needed

## Documentation
The project uses dokka for API documentation. When making changes, ensure that documentation is updated accordingly.

## Additional Resources
- [Documentation](https://www.jetbrains.com/help/exposed/home.html)
- [Migration Guide](https://www.jetbrains.com/help/exposed/migration-guide.html)
- [Breaking Changes](https://www.jetbrains.com/help/exposed/breaking-changes.html)
- [Slack Channel](https://kotlinlang.slack.com/messages/exposed/)
- [Issue Tracker](https://youtrack.jetbrains.com/issues/EXPOSED)
