plugins {
    `version-catalog`
    alias(libs.plugins.maven.publish)
}

group = "org.jetbrains.exposed"

catalog {
    versionCatalog {
        // Version reference
        version("exposed", project.version.toString())
        
        // Core libraries
        library("core", "org.jetbrains.exposed", "exposed-core").versionRef("exposed")
        library("dao", "org.jetbrains.exposed", "exposed-dao").versionRef("exposed")
        library("jdbc", "org.jetbrains.exposed", "exposed-jdbc").versionRef("exposed")
        
        // Date/Time libraries
        library("jodatime", "org.jetbrains.exposed", "exposed-jodatime").versionRef("exposed")
        library("java-time", "org.jetbrains.exposed", "exposed-java-time").versionRef("exposed")
        library("kotlin-datetime", "org.jetbrains.exposed", "exposed-kotlin-datetime").versionRef("exposed")
        
        // Additional features
        library("json", "org.jetbrains.exposed", "exposed-json").versionRef("exposed")
        library("crypt", "org.jetbrains.exposed", "exposed-crypt").versionRef("exposed")
        library("money", "org.jetbrains.exposed", "exposed-money").versionRef("exposed")
        
        // R2DBC
        library("r2dbc", "org.jetbrains.exposed", "exposed-r2dbc").versionRef("exposed")
        
        // Migration
        library("migration-core", "org.jetbrains.exposed", "exposed-migration-core").versionRef("exposed")
        library("migration-jdbc", "org.jetbrains.exposed", "exposed-migration-jdbc").versionRef("exposed")
        library("migration-r2dbc", "org.jetbrains.exposed", "exposed-migration-r2dbc").versionRef("exposed")
        
        // Spring
        library("spring-boot-starter", "org.jetbrains.exposed", "exposed-spring-boot-starter").versionRef("exposed")
        library("spring-transaction", "org.jetbrains.exposed", "spring-transaction").versionRef("exposed")
        
        // BOM
        library("bom", "org.jetbrains.exposed", "exposed-bom").versionRef("exposed")
    }
}
