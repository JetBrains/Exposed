[//]: # (title: Adding dependencies)

<show-structure for="chapter,procedure" depth="2"/>

Exposed is split into specific modules that give you the flexibility to only use the modules you need.
In this topic you'll learn what these modules are and how to add module dependencies to an existing Gradle/Maven project.

## Configure the repository

Exposed modules are available from the Maven Central repository.
To use them, add the appropriate dependency into your repository mapping:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin">
    repositories {
        mavenCentral()
    }
    </code-block>
  </tab>
  <tab title="Maven">
    The Maven Central repository is enabled by default for Maven users.
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy">
    repositories {
        mavenCentral()
    }
    </code-block>
  </tab>
</tabs>

## Add Exposed dependencies

Exposed consists of multiple modules that we've split into two categories:

- [Core modules](#core-modules)
- [Extension modules](#extension-modules)

### Core modules

To use Exposed in your application you need the following core modules:

| Module          | Function                                                                                                                                                         |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `exposed-core`  | Provides the foundational components and abstractions needed to work with databases in a type-safe manner and includes the Domain-Specific Language (DSL) API    |
| `exposed-dao`   | (Optional) Allows you to work with the Data Access Object (DAO) API. <br> It is only compatible with `exposed-jdbc` and does not work with `exposed-r2dbc`.</br> |
| `exposed-jdbc`  | Provides support for Java Database Connectivity (JDBC) with a transport-level implementation based on the Java JDBC API                                          |
| `exposed-r2dbc` | Provides support for Reactive Relational Database Connectivity (R2DBC)                                                                                           |

> You only need one transport module – either `exposed-jdbc` or `exposed-r2dbc`, not both.
> {style="note"}

Add the required Exposed modules to your project's dependencies:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin">
    dependencies {
        implementation("org.jetbrains.exposed:exposed-core:%exposed_version%")
        implementation("org.jetbrains.exposed:exposed-jdbc:%exposed_version%")
        implementation("org.jetbrains.exposed:exposed-dao:%exposed_version%") // Optional
    }
    </code-block>
  </tab>
  <tab title="Maven">
    <code-block lang="xml"><![CDATA[
        <dependencies>
            <dependency>
                <groupId>org.jetbrains.exposed</groupId>
                <artifactId>exposed-core</artifactId>
                <version>%exposed_version%</version>
            </dependency>
            <dependency>
                <groupId>org.jetbrains.exposed</groupId>
                <artifactId>exposed-jdbc</artifactId>
                <version>%exposed_version%</version>
            </dependency>
            <dependency>
                <groupId>org.jetbrains.exposed</groupId>
                <artifactId>exposed-dao</artifactId>
                <version>%exposed_version%</version>
            </dependency>
        </dependencies>
    ]]>
    </code-block>
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy">
    dependencies {
        implementation "org.jetbrains.exposed:exposed-core:%exposed_version%"
        implementation "org.jetbrains.exposed:exposed-jdbc:%exposed_version%"
        implementation "org.jetbrains.exposed:exposed-dao:%exposed_version%" //optional
    }
    </code-block>
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
| `exposed-migration-core`      | Provides core common functionality for database schema migrations                                                                                                               |
| `exposed-migration-jdbc`      | Provides utilities to support database schema migrations, with a reliance on a JDBC driver                                                                                      |
| `exposed-migration-r2dbc`     | Provides utilities to support database schema migrations, with a reliance on a R2DBC driver                                                                                     |


## Add a JDBC/R2DBC driver

You also need a JDBC or R2DBC driver for the database system you are using. For example, the following dependency
adds a JDBC driver for the H2 database:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin">
    dependencies {
        implementation("com.h2database:h2:%h2_db_version%")
    }
    </code-block>
  </tab>
  <tab title="Maven">
    <code-block lang="xml"><![CDATA[
    <dependencies>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.4.240</version>
        </dependency>
    </dependencies>
    ]]>
</code-block>
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy">
    dependencies {
        implementation "com.h2database:h2:%h2_db_version%"
    }
    </code-block>
  </tab>
</tabs>

> For the complete list of supported databases and their corresponding driver dependencies, see [](Working-with-Database.md).

## Add a logging dependency

To be able to see logs from `StdOutSqlLogger`, add a logging dependency:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin">
    dependencies {
        implementation("org.slf4j:slf4j-nop:%slf4j_version%")
    }
    </code-block>
  </tab>
  <tab title="Maven">
    <code-block lang="xml"><![CDATA[
        <dependencies>
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-nop</artifactId>
                <version>%slf4j_version%</version>
            </dependency>
        </dependencies>
        ]]>
    </code-block>
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy">
        dependencies {
            implementation("org.slf4j:slf4j-nop:%slf4j_version%")
        }
    </code-block>
  </tab>
</tabs>

> For more information on why a logging dependency is needed,
> see the [SLF4J Documentation](https://www.slf4j.org/codes.html#StaticLoggerBinder).
