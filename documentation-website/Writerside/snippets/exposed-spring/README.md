# Exposed Spring

This Spring Boot 4-based project uses Exposed for CRUD (Create, Read, Update, Delete) operations.

- [UserEntity.kt](src/main/kotlin/com/example/UserEntity.kt) describes the database schema.
- [UserService.kt](src/main/kotlin/com/example/UserService.kt) handles CRUD operations for user domains. This class
  determines transaction boundaries via `@Transactional`, fetches data via Exposed DSL, and handles domain objects.
- [UserController.kt](src/main/kotlin/com/example/UserController.kt) defines various endpoints that handle CRUD and calls
  `UserService` to process requests.
- [SchemaInitializer.kt](src/main/kotlin/com/example/SchemaInitialize.kt) initializes the database schema when the 
  application is run because the sample project uses h2.
- [SpringApplication.kt](src/main/kotlin/SpringApplication.kt) defines beans and imports the `Configuration` class. 
  Import `ExposedAutoConfiguration` in this file.

## Running

To run this sample, execute the following command in the repository's root directory:

```bash
./gradlew bootRun
```
Then, test the endpoints by sending requests from the `requests.http` file.
