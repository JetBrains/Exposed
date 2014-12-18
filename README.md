_NOTE:_ forked from [JetBrains version](https://github.com/JetBrains/Exposed) and added
gradle build, publishing to our open-source repo for people to use.
Also changed naming to kotlinx.* packages to keep this from being confused as core Kotlin.

Gradle build is setup top be used with Gradle [Pride plugin](https://github.com/prezi/pride) so
that it can be developed along side other code and have direct compile-time dependencies.  If you
don't know about the Pride plugin, you should. Same for [propdeps](https://github.com/spring-projects/gradle-plugins/tree/master/propdeps-plugin)
plugin adding provided and optional scopes to Gradle.

Logging is no SLF4j and no logging adapter is provided, assuming that your own application will include
one such as Logback.  Just as you must provide your own JDBC driver.

To use, add this repo to your Gradle build (or equivalent for Maven)

```
  maven {
        url 'https://collokia.artifactoryonline.com/collokia/collokia-oss'
    }
```

And add the dependency to: `org.kotlinx:kotlinx.sql:0.10.4-SNAPSHOT`

TODO: After using this more, update the documentation (it is possibly out of date)

...END notes about this fork...

Kotlin SQL Library
==================

see current samples in: [Samples.kt](https://github.com/Collokia/Exposed/blob/master/src/test/kotlin/kotlinx/samples/Samples.kt)
which currently looks like:

```kotlin
package kotlinx.sql.sample

import kotlinx.sql.*

object Users : Table() {
    val id = varchar("id", 10).primaryKey() // PKColumn<String>
    val name = varchar("name", length = 50) // Column<String>
    val cityId = (integer("city_id") references Cities.id).nullable() // Column<Int?>
}

object Cities : Table() {
    val id = integer("id").autoIncrement().primaryKey() // PKColumn<Int>
    val name = varchar("name", 50) // Column<String>
}

fun main(args: Array<String>) {
    var db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    // var db = Database("jdbc:mysql://localhost/test", driver = "com.mysql.jdbc.Driver", user = "root")

    db.withSession {
        create (Cities, Users)

        val saintPetersburgId = Cities.insert {
            it[name] = "St. Petersburg"
        } get Cities.id

        val munichId = Cities.insert {
            it[name] = "Munich"
        } get Cities.id

        Cities.insert {
            it[name] = "Prague"
        }

        Users.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[cityId] = saintPetersburgId
        }

        Users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[cityId] = munichId
        }

        Users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
        }

        Users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[cityId] = null
        }

        Users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[cityId] = null
        }

        Users.update({Users.id eq"alex"}) {
            it[name] = "Alexey"
        }

        Users.deleteWhere{Users.name like "%thing"}

        println("All cities:")

        for (city in Cities.selectAll()) {
            println("${city[Cities.id]}: ${city[Cities.name]}")
        }

        println("Manual join:")
        (Users join Cities).slice(Users.name, Cities.name).
            select {(Users.id.eq("andrey") or Users.name.eq("Sergey")) and
                    Users.id.eq("sergey") and Users.cityId.eq(Cities.id)} forEach {
            println("${it[Users.name]} lives in ${it[Cities.name]}")
        }

        println("Join with foreign key:")


        (Users join Cities).slice(Users.name, Users.cityId, Cities.name).
                select {Cities.name.eq("St. Petersburg") or Users.cityId.isNull()} forEach {
            if (it[Users.cityId] != null) {
                println("${it[Users.name]} lives in ${it[Cities.name]}")
            }
            else {
                println("${it[Users.name]} lives nowhere")
            }
        }

        println("Functions and group by:")

        (Cities join Users).slice(Cities.name, Users.id.count()).selectAll() groupBy Cities.name forEach {
            val cityName = it[Cities.name]
            val userCount = it[Users.id.count()]

            if (userCount > 0) {
                println("$userCount user(s) live(s) in $cityName")
            } else {
                println("Nobody lives in $cityName")
            }
        }

        drop (Users, Cities)

    }
}
```

And also the [tests](https://github.com/Collokia/Exposed/tree/master/src/test/kotlin/kotlinx/sql/tests/h2) can be used as examples.

_To execute SQL directly_ you may acquire the connection from an existing Exposed transaction using:

`val connection = Session.get().connection`