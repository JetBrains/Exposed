# Exposed-Spring-R2DBC

This Spring Boot 4 based project uses Exposed with R2DBC for CRUD (Create, Read, Update, Delete) operations.

- [UsersTable.kt](src/main/kotlin/domain/UsersTable.kt) and [IssuesTable.kt](src/main/kotlin/domain/IssuesTable.kt): Describe our database schema, in which
  each issue references the user it is assigned to. If you need to modify the structure, please take care to understand the existing design first.
- [UserService.kt](src/main/kotlin/service/UserService.kt): Handles CRUD operations for user domains. This class determines transaction boundaries via `@Transactional`
  on suspend functions, fetches data via Exposed DSL, and handles Domain objects. Creating a user along with their issues shows how all statements
  in a transaction are rolled back if one of them fails, which happens when two of the issues have the same title.
- [IssueService.kt](src/main/kotlin/service/IssueService.kt): Handles CRUD operations for issue domains in the same way, including a query that joins both tables.
- [UserController.kt](src/main/kotlin/controller/UserController.kt): Defines various endpoints that handle CRUD and call `UserService` to process requests.
- [IssueController.kt](src/main/kotlin/controller/IssueController.kt): Defines various endpoints that handle CRUD and call `IssueService` to process requests.
- [ControllerExceptionHandler.kt](src/main/kotlin/controller/ControllerExceptionHandler.kt): Responds with a conflict status when a request violates
  a database constraint.
- [application.yml](src/main/resources/application.yml): Configures the R2DBC connection and sets `spring.exposed.generate-ddl`, so that the database schema
  is initialized when the application is run.
- [Application.kt](src/main/kotlin/Application.kt): Starts the application and defines a custom database configuration bean, which replaces the default one
  provided by the Exposed starter's `ExposedReactiveAutoConfiguration`. The custom configuration sets a logger, so that every executed SQL statement
  is shown in the application's output. This could have similarly been done by setting `spring.exposed.show-sql`.

## Running

The sample connects to a PostgreSQL database named `sample_db` on `localhost:5432`, using the credentials set in
[application.yml](src/main/resources/application.yml). One way to start such a database is with Docker:

```bash
docker run --name exposed-sample-db -e POSTGRES_DB=sample_db -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:18
```

To run the sample, execute the following command in a repository's root directory:

```bash
./gradlew bootRun
```

## Testing

- [ApplicationTest.kt](src/test/kotlin/ApplicationTest.kt): Base class of all tests, which starts the application along with its own PostgreSQL database
  using Testcontainers.
- [UserControllerTest.kt](src/test/kotlin/UserControllerTest.kt) and [IssueControllerTest.kt](src/test/kotlin/IssueControllerTest.kt): Call all the user and issue endpoints
  over HTTP.
- [DatabaseConfigTest.kt](src/test/kotlin/DatabaseConfigTest.kt): Checks that the custom database configuration is used by Exposed, by reading the logged SQL statements.

Docker must be running to execute the tests:

```bash
./gradlew test
```

## Building a native image

The sample can be built as a GraalVM native image. The Exposed starter registers the runtime hints that Exposed needs,
so the sample does not have to define any hints itself.

### Change how the schema is created

Before building a native image, `spring.exposed.generate-ddl` must be removed from the sample. This property finds the tables
to create by scanning the classpath when the application starts, which is not possible in a native image. If the property
remains, no table(s) will be found and no schema will be generated.

1. Remove the `spring.exposed.generate-ddl` property from [application.yml](src/main/resources/application.yml).
2. Add a class that extends the starter's `DatabaseInitializer` and creates the schema by referring to the tables directly:

```kotlin
@Component
internal class SchemaInitializer(
    applicationContext: ApplicationContext,
    transactionManager: SpringReactiveTransactionManager,
) : DatabaseInitializer(applicationContext, emptyList(), TransactionalOperator.create(transactionManager)) {

    override suspend fun initializeSchema() {
        SchemaUtils.create(UsersTable, IssuesTable)
    }
}
```

`initializeSchema()` is invoked in a transaction when the application starts, before it accepts any requests.

### Build and run the image

Docker must be running to build the image, but GraalVM does not need to be installed:

```bash
./gradlew bootBuildImage
```

This creates an image named `exposed-spring-r2dbc:1.0.0`. The application in the container cannot reach a database on
`localhost`, so the following commands start the database and the application on the same Docker network instead, and
override the connection url of the application:

```bash
docker network create exposed-sample-net
docker run --name exposed-sample-db --network exposed-sample-net -e POSTGRES_DB=sample_db -e POSTGRES_PASSWORD=postgres -d postgres:18
docker run --network exposed-sample-net -p 8080:8080 -e SPRING_R2DBC_URL=r2dbc:postgresql://exposed-sample-db:5432/sample_db exposed-spring-r2dbc:1.0.0
```
