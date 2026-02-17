[//]: # (title: Spring Boot integration)

<show-structure for="chapter,procedure" depth="2"/>
<var name="artifact_name" value="exposed-spring-boot-starter"/>
<var name="artifact2_name" value="exposed-spring-boot4-starter"/>
<var name="example_name" value="exposed-spring"/>
<tldr>
    <p>
        <b>Required dependencies</b>: <code>org.jetbrains.exposed:%artifact_name%</code> or
        <code>org.jetbrains.exposed:%artifact2_name%</code>
    </p>
    <include from="lib.topic" element-id="jdbc-supported"/>
    <include from="lib.topic" element-id="r2dbc-not-supported"/>
    <include from="lib.topic" element-id="code_example"/>
</tldr>

Exposed provides Spring Boot 3 and Spring Boot 4 integration through the [Exposed Spring Boot starter](#add-dependencies).

The starter integrates Exposed with Spring Boot’s autoconfiguration model and transaction infrastructure. It registers
an Exposed-specific transaction manager and allows you to [configure Exposed](#configure-exposed) using standard Spring
Boot configuration properties.

It also contributes the required runtime hints for [GraalVM native image support](#graalvm-support), so you
can build native executables without additional configuration in most cases.

## Requirements

To use Exposed with Spring Boot, your project must meet the following requirements:

* Kotlin 2.1.x
* JDK 17 or newer

Spring Boot 3 and Spring Boot 4 both require JDK 17 or later. Ensure that your build tool (Gradle or Maven) and IDE target a compatible JDK version.

If you use Gradle, configure the JVM toolchain accordingly:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

## Add dependencies

To use Exposed with Spring Boot, add the `%artifact_name%` artifact to your build script:

<include from="lib.topic" element-id="add-dependency"/>

This starter includes the latest version of Exposed and its `spring-transaction` library along with the [Spring Boot Starter JDBC](http://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-jdbc).

### Spring Boot 4

For Spring Boot 4.x only support, use the `%artifact2_name%` artifact:

<var name="artifact_name" value="exposed-spring-boot4-starter"/>
<include from="lib.topic" element-id="add-dependency"/>

## Configure a database connection {id="configure-db"}

The starter depends on [`spring-boot-starter-jdbc`](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-jdbc),
so [all standard Spring Boot datasource properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#appendix.application-properties.data)
can be used to configure the database connection.

To configure a datasource, add the required properties to your
<path>src/resources/application.properties</path> file. The following example configures the connection for an H2
in-memory database:

```generic
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
```

## Configure Exposed {id="configure-exposed"}

To integrate Exposed with Spring’s transaction infrastructure, you need to do the following:

1. [Enable Exposed auto-configuration](#auto-config).
2. Optionally, [customize Exposed's default behavior](#custom-config) by registering a database configuration bean.

### Enable Exposed auto-configuration {id="auto-config"}

To ensure that Exposed’s transaction manager is used, you need to first enable it and disable Spring Boot’s default
`DataSourceTransactionManager` autoconfiguration.

You can apply the autoconfiguration directly to the class annotated with `@SpringBootApplication`:

```kotlin
import org.jetbrains.exposed.v1.spring.autoconfigure.ExposedAutoConfiguration
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

> For additional options for excluding autoconfiguration classes, see the [official Spring Boot documentation](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html#using.auto-configuration.disabling-specific).
> 
{style="tip"}

### Customize Exposed behavior {id="custom-config"}

To customize the default Exposed behavior, register a [`DatabaseConfig`](https://jetbrains.github.io/Exposed/api/exposed-spring-boot-starter/org.jetbrains.exposed.v1.spring.boot.autoconfigure/-exposed-auto-configuration/database-config.html)
bean:

```kotlin
import org.jetbrains.exposed.v1.spring.autoconfigure.ExposedAutoConfiguration
import org.jetbrains.exposed.v1.core.DatabaseConfig
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

> `ExposedAutoConfiguration` registers `@EnableTransactionManagement` with default attribute values. In particular:
> ```generic
> mode = AdviceMode.PROXY
> proxyTargetClass = false
> ```
> If you need different proxy settings (for example, class-based proxies), declare a separate `@EnableTransactionManagement`
> annotation on your main configuration class or configure the `spring.aop.proxy-target-class` property in your 
> <path>application.properties</path> file.
>
{style="note"}


### Enable automatic schema creation

To generate database schemas from Exposed table definitions at startup, set the `spring.exposed.generate-ddl`
property in your <path>application.properties</path> file:

```none
spring.exposed.generate-ddl=true
```

When enabled, the starter detects all classes extending `org.jetbrains.exposed.v1.sql.Table` and creates the schema
during application startup.

### Exclude packages

To exclude specific packages from automatic schema generation, use the `spring.exposed.excluded-packages` property
in your <path>application.properties</path> file:

```none
spring.exposed.excluded-packages=com.example.models.ignore,com.example.utils
```

This is useful for shared modules or tables managed outside the application lifecycle.

### Enable SQL logging

To log SQL statements executed by Exposed, enable the `spring.exposed.show-sql` property in your
<path>application.properties</path> file:

```none
spring.exposed.show-sql=true
```

This replaces the need to manually call `addLogger()` inside transactions and integrates with Spring Boot's
logging system.

## Manage transactions

The Exposed Spring Boot starter integrates directly with Spring’s declarative transaction model.

### Use `@Transactional` {id="transactional"}

By annotating a service class or method with Spring’s [`@Transactional`](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html),
Spring opens and closes the transaction for you.
Inside the method, you can freely use Exposed DSL or DAO APIs without wrapping your code in `transaction {}` blocks:

```kotlin
@Transactional
class MessageService {
    fun findMessageById(id: MessageId): Message? {
        return MessageEntity.selectAll().where { MessageEntity.id eq id.value }.firstOrNull()?.let {
            Message(
                id = MessageId(it[MessageEntity.id].value),
                text = it[MessageEntity.text]
            )
        }
    }
}
```

Spring opens the transaction before invoking the method and commits or rolls it back when the method completes.

### Register additional transaction managers

After configuring Exposed, you may still want to register additional transaction managers (for example, a plain JDBC or
JPA manager).

Define them in separate `@Configuration` classes as usual:

```kotlin
@Configuration
class JdbcTransactionManagerConfig {
    @Bean(name = ["jdbcTransactionManager"])
    fun jdbcTransactionManager(
        dataSource: DataSource
    ): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)
}
```

Select a specific transaction manager using the `transactionManager` attribute of `@Transactional`:

```kotlin
@Transactional(transactionManager = "jdbcTransactionManager")
fun doSomething() {
    // ...
}
```

### Define composed annotations

To reduce repetition, you can define composed annotations:

```kotlin
@Transactional(transactionManager = "springTransactionManager")
annotation class ExposedTransactional

@ExposedTransactional
fun doSomething() {
    // ...
}
```

### Configure the primary transaction manager

If you register multiple transaction managers, annotate the bean of the default one with `@Primary`. Spring then uses it
by default when `@Transactional` does not specify a manager.

## GraalVM native image support {id="graalvm-support"}

You can build a GraalVM native image of a Spring Boot application that uses the Exposed Spring starter without 
additional configuration.

The starter contributes the required runtime hints through `ExposedAotContribution`, which enables compatibility with
Spring Boot’s AOT (Ahead-of-Time) processing.

### AOT limitations

When you build a native image, Spring Boot applies AOT processing. AOT restricts dynamic configuration at runtime.

In particular, beans declared with `@ConditionalOnProperty` cannot change their behavior at runtime. As a result,
setting `spring.exposed.generate-ddl=true` does not enable automatic schema creation in a native image.

Instead, create the database schema programmatically. For example:

```kotlin
@Component
@Transactional
class SchemaInitialize : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        SchemaUtils.create(MessageEntity)
    }
}
```

### Resolve `KotlinReflectionInternalError: Unresolved class`

If the native image build or runtime fails with `KotlinReflectionInternalError: Unresolved class` the application likely
lacks required runtime hints for reflection.

To resolve this issue, implement `RuntimeHintsRegistrar` and register the missing types explicitly:

```kotlin
class ExposedHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.reflection().registerType(IntegerColumnType::class.java, *MemberCategory.entries.toTypedArray())
    }
}
```

To register your implementation, use one of the following approaches:
* Annotate a configuration class with `@ImportRuntimeHints`:
  ```kotlin
  @Configuration
  @ImportRuntimeHints(ExposedHints::class)
  class NativeHintsConfiguration
  ```
* Register the implementation in a `META-INF/spring.factories` file:
  ```generic
  org.springframework.aot.hint.RuntimeHintsRegistrar=<fully qualified class name>.ExposedHints
  ```
  Replace `com.example.ExposedHints` with the fully qualified name of your implementation.

> For more information, see [the Spring Boot documentation on GraalVM native images](https://docs.spring.io/spring-boot/reference/packaging/native-image/index.html).
>
{style="tip"}
