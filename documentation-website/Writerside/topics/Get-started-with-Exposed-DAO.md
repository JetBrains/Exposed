# Get started with Exposed DAO

<show-structure for="chapter,procedure" depth="2"/>
<tldr>
    <var name="example_name" value="get-started-with-exposed-dao"/>
    <include from="lib.topic" element-id="code_example"/>
</tldr>

<web-summary>
    In this tutorial, you’ll learn how to create and query a table in Kotlin with Exposed's DAO API.
</web-summary>
<link-summary>
    Learn how to create and query tables in Kotlin with Exposed's DAO API.
</link-summary>

In this tutorial, you’ll learn how to use Exposed’s DAO (Data Access Object) API to store and retrieve data in a
relational database by building a simple console application.

By the end, you’ll be able to:

- Configure a database connection.
- Define database tables and corresponding DAO entities.
- Perform basic CRUD operations using object-oriented style.

<include from="Getting-Started-with-Exposed.topic" element-id="prerequisites"/>
<var name="project_name" value="exposed-dao-kotlin-app"/>
<include from="Getting-Started-with-Exposed.topic" element-id="create-new-project"/>

## Add dependencies


<procedure>
Before you start using Exposed, you need to provide dependencies to your project.
<step>

Navigate to the **gradle/libs.versions.toml** file and define the Exposed and H2 version and libraries:

```kotlin
[versions]
//...
exposed = "%exposed_version%"
h2 = "%h2_db_version%"

[libraries]
//...
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-dao = { module = "org.jetbrains.exposed:exposed-dao", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
h2 = { module = "com.h2database:h2", version.ref = "h2" }
```

- The `exposed-core` module provides the foundational components and abstractions needed to work with databases in a
type-safe manner and includes the DSL API.
- The `exposed-dao` module allows you to work with the Data Access Object (DAO) API.
- The `exposed-jdbc` module is an extension of the <code>exposed-core</code> module that adds support for Java 
Database Connectivity (JDBC).

</step>
<step>

Navigate to the **app/build.gradle.kts** file and add the Exposed and H2 database modules into the `dependencies` block:

```kotlin
dependencies {
    //...
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    //...
}
```
</step>
<step>
    <include from="lib.topic" element-id="intellij_idea_load_gradle_changes"/>
</step>
</procedure>

## Configure a database connection

Every database access using Exposed is started by obtaining a connection and creating a transaction.
To configure the database connection, use the `Database.connect()` function.

<include from="Getting-Started-with-Exposed.topic" element-id="config-db-connection-procedure"/>

With this, you've added Exposed to your Kotlin project and configured a database connection.
You're now ready to define your data model and engage with the database using Exposed's DAO API.

## Define a table object

Exposed's DAO API provides the base `IdTable` class and its subclasses to define tables that use a standard id column
as the primary key.
To define the table object, follow the steps below.

<procedure id="define-table-object-procedure">
<step>

In the **app/src/main/kotlin/org/example/** folder, create a new **Task.kt** file.

</step>
<step>

Open **Task.kt** and add the following table definition:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/Task.kt" include-lines="1-2,5,8-13"}

In the `IntIdTable` constructor, passing the name `tasks` configures a custom
name for the table. Keep in mind that if no custom name is specified, Exposed will generate
one from the class name, which might lead to unexpected results.

Within the `Tasks` object, three columns are defined:

- `title` and `description` of type `String` are defined with the `varchar()` method.
- `isCompleted` of type `Boolean` is defined with the `bool()` method. Using the `default()` function, you configure the
default value to `false`.

The `IntIdTable` class will automatically generate an auto-incrementing integer id
column, to serve as the primary key for the table. At this point, you have defined a table with columns, which essentially creates the
blueprint for the `Tasks` table.

</step>
</procedure>

## Define an entity

When you use DAO approach, the `IntIdTable` needs to be associated with an `Entity`. This is because every database
record in this table needs to be mapped to an `Entity` instance, identified by its primary key.

To do that, add the following entity definition in your **Task.kt** file:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/Task.kt" include-lines="3-4,6-8,14-25"}

## Create and query a table

With Exposed’s DAO API, you can interact with a database using a type-safe syntax similar to Kotlin objects.
Before you start executing database operations, you must open a `transaction`.

<include from="Getting-Started-with-Exposed.topic" element-id="transaction-definition"/>

Navigate back to the **App.kt** file and add the following transaction function:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/App.kt" include-lines="1-2,4-33,43"}

First, you create the tasks table using the `SchemaUtils.create()` method. The `SchemaUtils` object holds utility 
methods for creating, altering, and dropping database objects.

Once the table has been created, you use the `IntEntity` extension method `.new()` to add two new `Task` records:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/App.kt" include-symbol="task1,task2"}

Within the `new` block, you set the values for each column. Exposed will translate the functions into the following
SQL queries:

```sql
INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED) VALUES ('Learn Exposed DAO', 'Follow the DAO tutorial', FALSE)
INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED) VALUES ('Read The Hobbit', 'Read chapter one', TRUE)
```

With the `.find()` method you then create a query to retrieve all entity instances with `isCompleted` set to `true`:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/App.kt" include-symbol="completed"}

Before you test the code, it would be handy to be able to inspect the SQL statements and queries Exposed sends to the
database. For this, you need to add a logger.

## Enable logging

At the beginning of your transaction block, add the following line to enable SQL query logging:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/App.kt" include-lines="11-15,42"}

## Run the application

<include from="lib.topic" element-id="intellij_idea_start_application"/>

The application will start in the **Run** tool window at the bottom of the IDE. There you will be able to see the SQL
logs along with the printed results:

```generic
SQL: SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'
SQL: CREATE TABLE IF NOT EXISTS TASKS (ID INT AUTO_INCREMENT PRIMARY KEY, "name" VARCHAR(128) NOT NULL, DESCRIPTION VARCHAR(128) NOT NULL, COMPLETED BOOLEAN DEFAULT FALSE NOT NULL)
SQL: INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED) VALUES ('Learn Exposed DAO', 'Follow the DAO tutorial', FALSE)
SQL: INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED) VALUES ('Read The Hobbit', 'Read chapter one', TRUE)
Created new tasks with ids 1 and 2
Completed tasks:
SQL: SELECT TASKS.ID, TASKS."name", TASKS.DESCRIPTION, TASKS.COMPLETED FROM TASKS WHERE TASKS.COMPLETED = TRUE
```

## Update and delete a task

Let’s extend the app’s functionality by updating and deleting the same task.

<procedure>
<step>

In the same `transaction()` function, add the following code to your implementation:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/App.kt" include-lines="12,14-16,35-42"}

You update the value of a property just as you would with any property in a Kotlin class:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/App.kt" include-lines="36"}

Similarly, to delete a task, you use the `.delete()` function on the entity:

```kotlin
```
{src="get-started-with-exposed-dao/src/main/kotlin/org/example/App.kt" include-lines="40"}

</step>
<step>
<include from="lib.topic" element-id="intellij_idea_restart_application"/>

You should now see the following result:

```generic
SQL: SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'
SQL: CREATE TABLE IF NOT EXISTS TASKS (ID INT AUTO_INCREMENT PRIMARY KEY, "name" VARCHAR(128) NOT NULL, DESCRIPTION VARCHAR(128) NOT NULL, COMPLETED BOOLEAN DEFAULT FALSE NOT NULL)
SQL: INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED) VALUES ('Learn Exposed DAO', 'Follow the DAO tutorial', FALSE)
SQL: INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED) VALUES ('Read The Hobbit', 'Read chapter one', TRUE)
Created new tasks with ids 1 and 2
SQL: SELECT COUNT(*) FROM TASKS WHERE TASKS.COMPLETED = TRUE
Completed tasks: 1
SQL: SELECT TASKS.ID, TASKS."name", TASKS.DESCRIPTION, TASKS.COMPLETED FROM TASKS WHERE TASKS.COMPLETED = TRUE
Task(id=2, title=Read The Hobbit, completed=true)
Updated task1: Task(id=1, title=Learn Exposed DAO, completed=true)
SQL: UPDATE TASKS SET COMPLETED=TRUE WHERE ID = 1
SQL: DELETE FROM TASKS WHERE TASKS.ID = 2
SQL: SELECT TASKS.ID, TASKS."name", TASKS.DESCRIPTION, TASKS.COMPLETED FROM TASKS
Remaining tasks: [Task(id=1, title=Learn Exposed DAO, completed=true)]
```
</step>
</procedure>
<include from="Getting-Started-with-Exposed.topic" element-id="second-transaction-behaviour-tip"/>

## Next steps

Way to go! You have now implemented a simple console application that uses Exposed's DAO API to fetch and modify task
data from an in-memory database. Now that you’ve covered the basics, you can explore everything DAO has to offer,
from [CRUD operations](DAO-CRUD-Operations.topic) to [Relationships](DAO-Relationships.topic) and more.
