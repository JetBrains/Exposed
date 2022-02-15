# PostgresSQL dialect DSL
Module with DSL for PostgresSQL only available in subpackage ``. 

Intended usage: your project run on postgres DB only so you can use specific dialect. Also check with 
official PostgresSQL, that version of your database supports all features, which you use in DSL.


```kotlin
// Cases which are not covered here may be covered in common SQL DSL or in tests in this module.
// Example assumes, that DB is already migrated to corresponding data model.
object Cities : Table() {
    val id = integer("id").autoIncrement() // Column<Int>
    val name = varchar("name", 50) // Column<String>

    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}

fun main() {
    Database.connect("jdbc:????", driver = "org.postgresql.Driver", user = "root", password = "")
    
    //insert  - support `on conflict do nothing|update`, `returning`
    transaction {
        val pragueId = Cities.insert {
            values { insertStatement ->
                insertStatement[name] = "Prague"
            } get Cities.id
        }
        
        
    }
}
```