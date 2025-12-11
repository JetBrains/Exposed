# exposed-bom
Bill of Materials for all Exposed modules

# Maven
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.jetbrains.exposed</groupId>
            <artifactId>exposed-bom</artifactId>
            <version>1.0.0-rc-4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-dao</artifactId>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-jdbc</artifactId>
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
    implementation(platform("org.jetbrains.exposed:exposed-bom:1.0.0-rc-4"))
    implementation("org.jetbrains.exposed", "exposed-core")
    implementation("org.jetbrains.exposed", "exposed-dao")
    implementation("org.jetbrains.exposed", "exposed-jdbc")
}
```
