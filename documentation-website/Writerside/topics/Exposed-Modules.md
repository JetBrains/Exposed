<show-structure for="chapter,procedure" depth="2"/>

# Modules

## Dependencies

Exposed modules are available from Maven Central repository.
To use them you have to add appropriate dependency into your repositories mapping.

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

## Base Modules

### Exposed 0.18.1 and higher

To move forward and support such features as Java 8 Time, async drivers, and so on, it was decided to split Exposed into
more specific modules. It will allow you to
take the only modules you need and will add flexibility in the future.

`Exposed` consists of the following modules:

* exposed-core - base module, which contains both DSL api along with mapping
* exposed-crypt - provides additional column types to store encrypted data in DB and encode/decode it on client-side
* exposed-dao - DAO api
* exposed-java-time - date-time extensions based on Java8 Time API
* exposed-jdbc - transport level implementation based on Java JDBC API
* exposed-jodatime - date-time extensions based on JodaTime library
* exposed-json - JSON and JSONB data type extensions
* exposed-kotlin-datetime - date-time extensions based on kotlinx-datetime
* exposed-money - extensions to support MonetaryAmount from "javax.money:money-api"
* exposed-spring-boot-starter - a starter for [Spring Boot](https://spring.io/projects/spring-boot) to utilize Exposed
  as the ORM instead
  of [Hibernate](https://hibernate.org/)

Dependencies mapping listed below is similar (by functionality) to the previous versions:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin">
      val exposedVersion: String = "0.58.0"
      dependencies {
          implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
          implementation("org.jetbrains.exposed:exposed-crypt:$exposedVersion")
          implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
          implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
          // optional jodatime
          implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVersion")
          // optional java-time
          implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
          // optional kotlin-datetime
          implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
          // optional json
          implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
          // optional money
          implementation("org.jetbrains.exposed:exposed-money:$exposedVersion")
          // optional spring-boot
          implementation("org.jetbrains.exposed:exposed-spring-boot-starter:$exposedVersion")
      }
    </code-block>
  </tab>
  <tab title="Maven">
    <code-block lang="xml">
&lt;dependencies&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-core&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-crypt&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-dao&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-java-time&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-jdbc&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-jodatime&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-json&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-kotlin-datetime&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-money&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
        &lt;artifactId&gt;exposed-spring-boot-starter&lt;/artifactId&gt;
        &lt;version&gt;0.58.0&lt;/version&gt;
    &lt;/dependency&gt;
&lt;/dependencies&gt;
    </code-block>
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy">
      def exposedVersion = "0.58.0"
      dependencies {
          implementation "org.jetbrains.exposed:exposed-core:$exposedVersion"
          implementation "org.jetbrains.exposed:exposed-crypt:$exposedVersion"
          implementation "org.jetbrains.exposed:exposed-dao:$exposedVersion"
          implementation "org.jetbrains.exposed:exposed-jdbc:$exposedVersion"
          // optional jodatime
          implementation "org.jetbrains.exposed:exposed-jodatime:$exposedVersion"
          // optional java-time
          implementation "org.jetbrains.exposed:exposed-java-time:$exposedVersion"
          // optional kotlin-datetime
          implementation "org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion"
          // optional json
          implementation "org.jetbrains.exposed:exposed-json:$exposedVersion"
          // optional money
          implementation "org.jetbrains.exposed:exposed-money:$exposedVersion"
          // optional spring-boot
          implementation "org.jetbrains.exposed:exposed-spring-boot-starter:$exposedVersion"
      }
    </code-block>
  </tab>
</tabs>

### JDBC driver and logging

You also need a JDBC driver for the database system you are using
(see [Working with Databases](Working-with-Database.md)) and a logger
for `addLogger(StdOutSqlLogger)`.
Example (Gradle
syntax):

```kotlin
dependencies {
    // for H2
    implementation("com.h2database:h2:2.1.214")
    // for logging (StdOutSqlLogger), see
    // http://www.slf4j.org/codes.html#StaticLoggerBinder
    implementation("org.slf4j:slf4j-nop:1.7.30")
}
```

### Exposed 0.17.x and lower

Prior Exposed 0.18.1 there was only one base module `exposed` which contains everything you may need including JodaTime
as date-time library.
To add `Exposed` framework of that version, you had to use:

<tabs>
  <tab title="Kotlin Gradle">
    <code-block lang="kotlin">
    dependencies {
        implementation("org.jetbrains.exposed:exposed:0.17.7")
    }
    </code-block>
  </tab>
  <tab title="Maven">
    <code-block lang="xml">
    &lt;dependencies&gt;
        &lt;dependency&gt;
            &lt;groupId&gt;org.jetbrains.exposed&lt;/groupId&gt;
            &lt;artifactId&gt;exposed&lt;/artifactId&gt;
            &lt;version&gt;0.17.7&lt;/version&gt;
        &lt;/dependency&gt;
    &lt;/dependencies&gt;
    </code-block>
  </tab>
  <tab title="Groovy Gradle">
    <code-block lang="groovy">
    dependencies {
        implementation("org.jetbrains.exposed:exposed:0.17.7")
    }
    </code-block>
  </tab>
</tabs>
