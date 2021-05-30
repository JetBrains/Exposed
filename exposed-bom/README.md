# exposed-bom
Bill of Materials for all Exposed modules

# Maven
```xml
<!-- Versions after 0.30.1 -->
<repositories>
    <repository>
        <id>mavenCentral</id>
        <name>mavenCentral</name>
        <url>https://repo1.maven.org/maven2/</url>
    </repository>
</repositories>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.jboss.bom</groupId>
            <artifactId>jboss-javaee-6.0-with-tools</artifactId>
            <version>0.31.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-core</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-dao</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-jdbc</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

# Gradle
```kotlin
repositories {
  // Versions after 0.30.1
  mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.exposed:exposed-bom:0.31.2"))
    implementation("org.jetbrains.exposed", "exposed-core")
    implementation("org.jetbrains.exposed", "exposed-dao")
    implementation("org.jetbrains.exposed", "exposed-jdbc")
}
```
