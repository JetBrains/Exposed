# exposed-bom
Bill of Materials for all Exposed modules

# Maven
```xml
<!-- Versions after 0.33.1 -->
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
            <groupId>io.taff.exposed</groupId>
            <artifactId>exposed-bom</artifactId>
            <version>0.4.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.taff.exposed</groupId>
        <artifactId>exposed-core</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>io.taff.exposed</groupId>
        <artifactId>exposed-dao</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>io.taff.exposed</groupId>
        <artifactId>exposed-jdbc</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

# Gradle
```kotlin
repositories {
  // Versions after 0.33.1
  mavenCentral()
}

dependencies {
    implementation(platform("io.taff.exposed:exposed-bom:0.4.0"))
    implementation("io.taff.exposed", "exposed-core")
    implementation("io.taff.exposed", "exposed-dao")
    implementation("io.taff.exposed", "exposed-jdbc")
}
```
