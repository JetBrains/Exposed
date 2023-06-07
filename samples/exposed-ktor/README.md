# Exposed-Ktor

This Ktor based project uses Exposed for CRUD (Create, Read, Update, Delete) operations. Here's how they work:

- [UsersSchema.kt](src/main/kotlin/plugins/UsersSchema.kt): Describes our database schema. If you need to modify the structure, please take care to
  understand the existing design first.
- [Databases.kt](src/main/kotlin/plugins/Databases.kt): Handles CRUD operations with various endpoints.
  Backend application with CRUD endpoints built using Ktor and Exposed.

## Running

To run the sample, execute the following command in a repository's root directory:

```bash
./gradlew run
```
