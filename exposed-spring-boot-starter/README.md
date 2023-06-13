# Exposed Spring Boot Starter

This is a starter for [Spring Boot](https://spring.io/projects/spring-boot) to utilize [Exposed](https://github.com/JetBrains/Exposed) as the ORM instead of [Hibernate](https://hibernate.org/)

## Getting Started
This starter will give you the latest version of [Exposed](https://github.com/JetBrains/Exposed) and the spring-transaction library along with [Spring Boot Data Starter JDBC](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-data-jdbc)
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
    <version>0.41.1</version>
  </dependency>
</dependencies>
```
### Gradle
```groovy
repositories {
    mavenCentral()
}
dependencies {
  implementation 'org.jetbrains.exposed:exposed-spring-boot-starter:0.41.1'
}
```

## Setting up a database connection
This starter utilizes spring-boot-starter-data-jdbc so all properties that you are used to for setting up a database in spring are applicable here.

### application.properties (h2 example)
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
```

### Configuring Exposed 
When using this starter, you can customize typical Exposed configuration by registering a [DatabaseConfig](https://github.com/JetBrains/Exposed/blob/master/exposed-core/src/main/kotlin/org/jetbrains/exposed/sql/DatabaseConfig.kt). See the class itself for available configuration options.

Example:

```kotlin
@Configuration
class ExposedConfig {
  @Bean
  fun databaseConfig() = DatabaseConfig {
    useNestedTransactions = true
  }
}
```

## Automatic Schema Creation
This starter will create the database schema if enabled automatically using any class that extends `org.jetbrains.exposed.sql.Table`

Sometimes you will want to exclude packages from that list, we have included the property `spring.exposed.excluded-packages` which will exclude everything under the provided package

### application.properties
```properties
spring.exposed.generate-ddl = true
spring.exposed.excluded-packages = com.example.models.ignore,com.example.utils
```
