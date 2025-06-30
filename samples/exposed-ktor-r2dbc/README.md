# Exposed-Ktor-R2DBC

This Ktor project uses Exposed with a PostgreSQL R2DBC driver to implement a basic project-issue tracker.
The project follows a Domain-Driven Design approach to separate the functionality of each of the four domains:
`User`, `Project`, `Issue`, and `Comment`.

```
src/
├── main/
│   └── kotlin/
│       ├── domain/
│       │   ├── comment/
│       │   └── issue/
│       │       ├── Issue.kt            # Issue data class
│       │       ├── IssueRepository.kt  # Exposed database operations
│       │       ├── IssueRoutes.kt      # Ktor route handler
│       │       ├── IssueService.kt     # Service level
│       │       └── IssuesTable.kt      # Exposed table object, convertors
│       │   ├── project/
│       │   ├── user/
│       │   └── BaseRepository.kt
│       ├── plugins/                    # Ktor plugin configuration
│       │   ├── Database.kt             # Exposed database connection, schema setup
│       │   ├── Monitoring.kt
│       │   ├── Routing.kt
│       │   └── Serialization.kt
└──     └── Application.kt              # Application entry point
```

## API Endpoints

Example endpoints can be viewed in [endpoints.http](endpoints.http).

## Running the Project

1. Clone the repository and/or extract the sample project
2. Run Ktor Gradle task `buildFatJar`
3. Run `docker compose up`
4. The server responds at http://localhost:8080 to check routes, if needed.
   * In [endpoints.http](endpoints.http), either select `Run all requests in file` and check stored results, or run each request individually to view results in console. 
