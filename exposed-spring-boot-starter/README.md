# Exposed Spring Boot Starter

This is a starter for [Spring Boot](https://spring.io/projects/spring-boot) to utilize [Exposed](https://github.com/JetBrains/Exposed) as the ORM instead of [Hibernate](https://hibernate.org/).

## Getting Started
This starter will give you the latest version of [Exposed](https://github.com/JetBrains/Exposed) and its `spring-transaction` library along with the [Spring Boot Starter Data JDBC](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-data-jdbc).
### Maven
```mxml
<repositories>
  <repository>
    <id>mavenCentral</id>
    <name>mavenCentral</name>
    <url>https://repo1.maven.org/maven2/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>org.jetbrains.exposed</groupId>
    <artifactId>exposed-spring-boot-starter</artifactId>
    <version>0.45.0</version>
  </dependency>
</dependencies>
```
### Gradle Groovy
```groovy
repositories {
    mavenCentral()
}
dependencies {
    implementation 'org.jetbrains.exposed:exposed-spring-boot-starter:0.45.0'
}
```
### Gradle Kotlin DSL
In `build.gradle.kts`:
```kotlin
val exposedVersion: String by project
repositories {
    mavenCentral()
}
dependencies {
    implementation("org.jetbrains.exposed:exposed-spring-boot-starter:$exposedVersion")
}
```
In `gradle.properties`
```properties
exposedVersion=0.45.0
```

## Setting up a database connection
This starter utilizes `spring-boot-starter-data-jdbc` so that all properties usually used for setting up a database in Spring are applicable here.

### application.properties (h2 example)
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
```

## Configuring Exposed
When using this starter, you can customize the default Exposed configuration by registering a [DatabaseConfig](https://github.com/JetBrains/Exposed/blob/master/exposed-core/src/main/kotlin/org/jetbrains/exposed/sql/DatabaseConfig.kt). See the class itself for available configuration options.

Example:

```kotlin
import org.jetbrains.exposed.spring.autoconfigure.ExposedAutoConfiguration
import org.jetbrains.exposed.sql.DatabaseConfig
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ImportAutoConfiguration(
    value = [ExposedAutoConfiguration::class],
    exclude = [DataSourceTransactionManagerAutoConfiguration::class]
)
class ExposedConfig {
    @Bean
    fun databaseConfig() = DatabaseConfig {
        useNestedTransactions = true
    }
}
```
In addition to applying the `ExposedAutoConfiguration` class, it is recommended that the `DataSourceTransactionManagerAutoConfiguration` class be excluded from auto-configuration.
This can be done as part of a custom configuration, as shown above.

Alternatively, auto-configuration can be detailed directly on the Spring configuration class that is annotated using `@SpringBootApplication`:

```kotlin
import org.jetbrains.exposed.spring.autoconfigure.ExposedAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication
@ImportAutoConfiguration(
    value = [ExposedAutoConfiguration::class],
    exclude = [DataSourceTransactionManagerAutoConfiguration::class]
)
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

See the [official documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#using.auto-configuration.disabling-specific) for more options to exclude auto-configuration classes.

**Note:** The `ExposedAutoConfiguration` class adds `@EnableTransactionManagement` without setting any attributes. 
This means that all attributes have their default values, including `mode = AdviceMode.PROXY` and `proxyTargetClass = false`. 
If the type of [proxying mechanism](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)  is unexpected, 
the attributes can be set to the required values in a separate `@EnableTransactionManagement` on the main configuration class 
or in the `application.properties` file using the property `spring.aop.proxy-target-class`.

### Configuring Additional Transaction Managers

Once Exposed has been configured as shown [above](#configuring-exposed), other transaction managers can also be registered by creating their own `@Configuration` class containing a declared transaction manager bean.

To then determine which manager should be used, set the `transactionManager` attribute of `@Transactional` to the qualifier value of the independent transaction manager bean.

Additionally, [custom composed annotations](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html#tx-custom-attributes) can be defined to reduce repetitive code when switching between these transaction managers:
```kotlin
@Transactional(transactionManager = "springTransactionManager")
annotation class ExposedTransactional

@ExposedTransactional
fun doSomething() {
    // ...
}
```

If one of these transaction managers is used predominantly, the `@Primary` annotation can also be used on its bean to give it higher preference.
This ensures that the annotated transaction manager will be the autowired value when `@Transactional` is used without setting the `transactionManager` attribute.

See the [official documentation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html#tx-multiple-tx-mgrs-with-attransactional) for more details about handling multiple transaction managers.

## Automatic Schema Creation
Including the property `spring.exposed.generate-ddl` enables this starter to create the database schema automatically using any class that extends `org.jetbrains.exposed.sql.Table`.

Sometimes you will want to exclude packages from the list of auto-created schemas. In this event, the property `spring.exposed.excluded-packages` can be used to exclude everything under the provided packages.

### application.properties
```properties
spring.exposed.generate-ddl=true
spring.exposed.excluded-packages=com.example.models.ignore,com.example.utils
```

## Logging SQL Statements
Instead of invoking `addLogger()` to log all database transaction statements, add the property `spring.exposed.show-sql` to your configuration file.

### application.properties
```properties
spring.exposed.show-sql=true
```

## Sample

Check out the [Exposed - Spring Boot sample](../samples/exposed-spring/README.md) for more details, for example:
- How to set up [a controller](../samples/exposed-spring/src/main/kotlin/controller/UserController.kt) to handle web requests
- How to implement [a service layer](../samples/exposed-spring/src/main/kotlin/service/UserService.kt) for database access logic
