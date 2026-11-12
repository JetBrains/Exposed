# exposed-spring-data-starter

Spring Boot 4 auto-configuration for Exposed Spring Data repositories. Add this starter to a Spring Boot application to enable repository scanning in the application's auto-configuration packages; `@EnableExposedRepositories` is not required.

The starter builds on `exposed-spring-boot4-starter`, uses its `springTransactionManager`, and requires a JDBC `DataSource`. It also exposes that manager as `transactionManager` for Spring's default transactional lookup, unless the application already defines a bean with that name.

Repository scanning can be configured with these application properties:

| Property | Default | Description |
| --- | --- | --- |
| `spring.data.exposed.repositories.enabled` | `true` | Set to `false` to disable repository auto-configuration. |
| `spring.data.exposed.repositories.bootstrap-mode` | `default` | Repository bootstrap mode: `default`, `deferred`, or `lazy`. |

Declare repositories with `@ExposedRepository` and extend `ExposedCrudRepository`, as described in the [Spring Data module documentation](../exposed-spring-data/README.md).