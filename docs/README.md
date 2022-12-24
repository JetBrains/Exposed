<div align="center">
<img  align="center" src="./logo.png" alt="Exposed" width="315" /></div>
<br><br>

![Build](https://github.com/tpasipanodya/exposed/actions/workflows/.github/workflows/cicd.yml/badge.svg)

[![GitHub License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)


Welcome to **Exposed**; a fork of the [Kotlin ORM framework](https://github.com/JetBrains/Exposed).
In terms of behavior, this fork is identical to the main [Exposed](https://github.com/JetBrains/Exposed)
library with the exception that it allows configuring default filters (query operators) on tables. Default filters can
be useful for implementing multi-tenancy & soft deletes.


## Supported Databases
Visit [the official Exposed page](https://github.com/JetBrains/Exposed) for an updated list.

## Getting started

```kotlin
val exposedVersion: String by project
repositories {
    maven("https://tmpasipanodya.jfrog.io/artifactory/releases")
}

dependencies {
    implementation(platform("io.taff.exposed:exposed-bom:0.8.1"))
    implementation("io.taff.exposed", "exposed-core")
    implementation("io.taff.exposed", "exposed-dao")
    implementation("io.taff.exposed", "exposed-jdbc")
}
```

The latest release version is `0.6.0` (pegged against `org.jetbrains.exposed:exposed:0.38.1`)
and is available on JFrog at `https://tmpasipanodya.jfrog.io/artifactory/releases`.

## Examples

```kotlin
CurrentUserId = ThreadLocal<Long>()

object users : LongIdTable() {
    val name = varchar("name", length = 50) // Column<String>
}

object posts : LongIdTable() {
    val text = text("text")
    val userId = integer("user_id") references users.id

    override val defaultFilter = { Op.build { userId eq CurrentUserId.get() } }
}

fun main() {
    Database.connect(db_url)
    transaction {
        SchemaUtils.create(users, posts)

        val user1Id = users.insert { it[name] = "User 1" }[users.id]
        val user2Id = users.insert { it[name] = "User 2" }[users.id]

        posts.insert {
            it[userId] = user1Id
            it[text] = "foo bar"
        }
        posts.insert {
            it[userId] = user2Id
            it[text] = "lorem ipsum"
        }

        CurrentUserId.set(user1Id)
        var retrievedText = posts.selectAll().single()[posts.text]

       
        println(retrievedText == "foo bar")
        //> true

        CurrentUserId.set(user2Id)
        retrievedText = posts.selectAll().single()[posts.text]

        
        println(retrievedText == "lorem ipsum")
        //> true

        // Any persisted record must have an id but the following update statment 
        // applies the table's default filter, hence only the current user's post 
        // will be updated.
        val newText =  "Let's get it started in here"
        posts.update({ id.isNotNull() }) { it[text] = newText }

        posts.stripDefaultFilter()
            .orderBy(posts.userId, SortOrder.Asc)
            .selectAll()
            .map { it[posts.userId] to it[posts.text] }
            .let { allTexts -> 
                // true
                listOf(
                    user1Id to "foo bar", 
                    user2Id to newText
                ) == allTexts
            }
    }
}
```
Default filters are applied to all operations including deletes, joins, unions, etc.
For additional examples, take a look at 
[the official Exposed wiki](https://github.com/JetBrains/Exposed/wiki).

## Development

To initialize test containers and other local development fixtures:
```shell
.scripts/setup.sh
```

## Links

* [Wiki](https://github.com/JetBrains/Exposed/wiki) with examples and docs.
* [Change log](ChangeLog.md) of improvements and bug fixes.

Feel free to submit issues and requests via Github.

## License

By contributing to the Open Sauced project, you agree that your contributions will be licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
