<show-structure for="chapter,procedure" depth="2"/>

# Modules

Exposed is split into specific modules that give you the flexibility to only use the modules you need.
In this topic you'll learn what these modules are and how to add module dependencies to an existing Gradle/Maven project.

## Configure the repository

Exposed modules are available from the Maven Central repository.
To use them, add the appropriate dependency into your repositories mapping:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin" src="exposed-modules-kotlin-gradle/build.gradle.kts" include-lines="8-10"/>
  </tab>
  <tab title="Maven">
    The Maven Central repository is enabled by default for Maven users.
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy" src="exposed-modules-groovy-gradle/build.gradle" include-lines="8-10"/>
  </tab>
</tabs>

## Add Exposed dependencies

Exposed consists of multiple modules that we've split into two categories:

- [Core modules](#core-modules)
- [Extension modules](#extension-modules)

### Core modules

To use Exposed in your application you need the following core modules:

| Module          | Function                                                                                                                                                      |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `exposed-core`  | Provides the foundational components and abstractions needed to work with databases in a type-safe manner and includes the Domain-Specific Language (DSL) API |
| `exposed-dao`   | (Optional) Allows you to work with the Data Access Object (DAO) API                                                                                           |
| `exposed-jdbc`  | Provides support for Java Database Connectivity (JDBC) with a transport-level implementation based on the Java JDBC API                                       |
| `exposed-r2dbc` | Provides support for Reactive Relational Database Connectivity (R2DBC)                                                                                        |

Add the required Exposed modules to your project's dependencies:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin" src="exposed-modules-kotlin-gradle/build.gradle.kts" include-lines="12-15,19"/>
  </tab>
  <tab title="Maven">
    <code-block lang="xml" src="exposed-modules-maven/pom.xml" include-lines="68,86-100,111"/>
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy" src="exposed-modules-groovy-gradle/build.gradle" include-lines="12-15,19"/>
  </tab>
</tabs>

### Extension modules

The following modules extend Exposed's capabilities, allowing you to work with specific data types, encryption,
and date-time handling:

| Module                        | Function                                                                                                                                                                        |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `exposed-crypt`               | Provides additional column types to store encrypted data in the database and encode/decode it on the client-side                                                                |
| `exposed-java-time`           | Date-time extensions based on the [Java 8 Time API](https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html)                                                   |
| `exposed-jodatime`            | Date-time extensions based on the [Joda-Time](https://www.joda.org/joda-time/) library                                                                                          |
| `exposed-json`                | JSON and JSONB data type extensions                                                                                                                                             |
| `exposed-kotlin-datetime`     | Date-time extensions based on the [`kotlinx-datetime`](https://kotlinlang.org/api/kotlinx-datetime/) library                                                                    |
| `exposed-money`               | Extensions to support [`MonetaryAmount`](https://javamoney.github.io/apidocs/java.money/javax/money/MonetaryAmount.html) from the [JavaMoney API](https://javamoney.github.io/) |
| `exposed-spring-boot-starter` | A starter for [Spring Boot](https://spring.io/projects/spring-boot) to utilize Exposed as the ORM instead of [Hibernate](https://hibernate.org/)                                |
| `spring-transaction`          | Transaction manager that builds on top of Spring's standard transaction workflow                                                                                                |
| `exposed-migration`           | Provides utilities to support database schema migrations                                                                                                                        |


## Add a JDBC driver

You also need a JDBC driver for the database system you are using. For example, the following dependency
adds a JDBC driver for the H2 database:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin" src="exposed-modules-kotlin-gradle/build.gradle.kts" include-lines="12,16,19"/>
  </tab>
  <tab title="Maven">
    <code-block lang="xml" src="exposed-modules-maven/pom.xml" include-lines="68,101-105,111"/>
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy" src="exposed-modules-groovy-gradle/build.gradle" include-lines="12,16,19"/>
  </tab>
</tabs>

> For the complete list of supported databases and their corresponding driver dependency, see [](Working-with-Database.md).

## Add a logging dependency

To be able to see logs from `StdOutSqlLogger`, add a logging dependency:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin" src="exposed-modules-kotlin-gradle/build.gradle.kts" include-lines="12,17,19"/>
  </tab>
  <tab title="Maven">
    <code-block lang="xml" src="exposed-modules-maven/pom.xml" include-lines="68,106-111"/>
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy" src="exposed-modules-groovy-gradle/build.gradle" include-lines="12,17,19"/>
  </tab>
</tabs>

> For more information on why a logging dependency is needed,
> see the [SLF4J Documentation](https://www.slf4j.org/codes.html#StaticLoggerBinder).
