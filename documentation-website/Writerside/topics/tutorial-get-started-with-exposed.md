# Get started with Exposed, an ORM framework for Kotlin

<show-structure for="chapter,procedure" depth="2"/>
<var name="project_name" value="exposed-kotlin-app"/>
<tldr>
    <var name="example_name" value="get-started-with-exposed"/>
    <include from="lib.topic" element-id="code_example"/>
</tldr>

<web-summary>
    In this tutorial, you’ll learn how to create and query a table in Kotlin with Exposed's DSL API.
</web-summary>
<link-summary>
    Learn how to create and query tables in Kotlin with Exposed's DSL API.
</link-summary>

In this tutorial, you’ll learn how to use Exposed’s Domain-Specific Language (DSL) API to store and retrieve
data in a relational database by building a simple console application.

By the end of this tutorial, you’ll be able to do the following:

* Configure database connections using an in-memory database.
* Define database tables using Exposed’s DSL.
* Perform basic CRUD (Create, Read, Update, and Delete) operations on the database.

## Prerequisites {id="prerequisites"}

Before starting this tutorial, ensure that you have the following installed on your machine:

* The latest
  [Gradle](https://gradle.org/install/?_gl=1*h4crdf*_gcl_au*MTQ5NTIyNjcyMy4xNzE3MDc4MDIz*_ga*MTYzNzI5NzkyLjE2OTk4OTg3MTE.*_ga_7W7NC6YNPT*MTcxOTQwNjQyMS4xOC4xLjE3MTk0MDc2NzUuMzUuMC4w)
  distribution.
* A [Java Development Kit (JDK)](https://www.oracle.com/java/technologies/downloads/), version 8 or higher.
* An integrated development environment (IDE), such as [IntelliJ IDEA](https://www.jetbrains.com/idea/).

  > We recommend that you install [IntelliJ IDEA Ultimate](https://www.jetbrains.com/idea/buy/), which comes with
  > built-in [database tools](https://www.jetbrains.com/pages/intellij-idea-databases/) and supports
  > [the Exposed plugin](https://www.jetbrains.com/help/idea/exposed.html). The Exposed plugin provides code completion,
  > inspections, and allows you to generate tables and entities from an existing schema.
  >
  {style="note"}

## Create a new project {id="create-new-project"}

First, you will need a basic Kotlin project setup to build upon. You can
<resource src="init-kotlin-gradle-app.zip">download a pre-initialized project</resource> or follow the steps
below to generate a new project with Gradle.

<procedure id="create-new-project-procedure">
<step>

In a terminal window, navigate to the destination where you want to create your project and run
the following commands to create a new folder and change directory into it:

```console
mkdir %project_name%
cd %project_name%
```
{prompt="$"}

</step>
<step>

Run the `gradle init` task to initialize a new Gradle project:

```console
gradle init
```
{prompt="$"}

When prompted, select the following options:

- `1: Application` project type.
- `2: Kotlin` implementation language.

For the other questions, press enter to use the default values.
The output will look like the following:

```console
Select type of build to generate:
    1: Application
    2: Library
    3: Gradle plugin
    4: Basic (build structure only)
Enter selection (default: Application) [1..4]
Select implementation language:
    1: Java
    2: Kotlin
    3: Groovy
    4: Scala
    5: C++
    6: Swift
Enter selection (default: Java) [1..6] 2
Enter target Java version (min: 7, default: 21):
Project name (default: exposed-kotlin-app):
Select application structure:
    1: Single application project
    2: Application and library project
Enter selection (default: Single application project) [1..2]
Select build script DSL:
    1: Kotlin
    2: Groovy
Enter selection (default: Kotlin) [1..2]
Select test framework:
    1: kotlin.test
    2: JUnit Jupiter
Enter selection (default: kotlin.test) [1..2]
Generate build using new APIs and behavior (some features may change in the next minor release)? (default: no) [yes, no]
> Task :init
To learn more about Gradle by exploring our Samples at https://docs.gradle.org/8.8/samples/sample_building_kotlin_applications.html
BUILD SUCCESSFUL in 28s
1 actionable task: 1 executed
```
{noinject="true"}

</step>
<step>

Once the project has been initialized, open the project folder in your IDE.
To open the project in IntelliJ IDEA, use the following command:

```shell
idea .
```

</step>
</procedure>

## Add dependencies {id="add-dependencies"}

Before you start using Exposed, you need to add dependencies to your project.

<procedure id="add-dependencies-procedure">
<step>

Navigate to the <path>gradle/libs.versions.toml</path> file and define the Exposed and H2 version and artifacts:

```toml
[versions]
exposed = "%exposed_version%"
h2 = "%h2_db_version%"

[libraries]
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
h2 = { module = "com.h2database:h2", version.ref = "h2" }
```
{noinject="true"}

* The `exposed-core` module provides the foundational components and abstractions
  needed to work with databases in a type-safe manner and includes the DSL API.
* The `exposed-jdbc` module is an extension of the `exposed-core` module
  that adds support for Java Database Connectivity (JDBC).

</step>
<step>

Navigate to the <path>app/build.gradle.kts</path> file and add the Exposed and H2 database modules into the
`dependencies {}` block:

```kotlin
dependencies {
    //...
    implementation(libs.exposed.core)
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

## Configure a database connection {id="configure-db-connection"}

Whenever you access a database using Exposed, you start by obtaining a connection and creating a transaction.
To configure the database connection, use the `Database.connect()` function.

<procedure id="config-db-connection-procedure">
<step>

Navigate to <path>app/src/main/kotlin/org/example/</path> and open the <path>App.kt</path> file.

</step>
<step>

Replace the contents of the <path>App.kt</path> file with the following implementation:

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-lines="4-5,8,16-18,59"}

The `Database.connect()` function creates an instance of a class that represents
the database and takes two or more parameters. In this case, the connection URL and the driver.

* `jdbc:h2:mem:test` is the database URL to connect to:
  * `jdbc` specifies that this is a JDBC connection.
  * `h2` indicates that the database is an H2 database.
  * `mem` specifies that the database is in-memory, meaning the data will only
    exist in memory and will be lost when the application stops.
  - `test` is the name of the database.
* `org.h2.Driver` specifies the H2 JDBC driver to be used for establishing the connection.

> Invoking `Database.connect()` only configures the connection settings,
> but it does not immediately establish a connection with the database. The actual connection
> to the database will be established later when a database operation is performed.
>
{style="note"}

> By default, Exposed automatically registers the database connection. You can change
> this behavior by setting the `connectionAutoRegistration` parameter
> when calling `Database.connect()`.
>
{style="note"}

</step>
</procedure>

With this, you've added Exposed to your Kotlin project and configured a database connection.
You're now ready to define your data model and engage with the database using Exposed's DSL.

## Define a table object {id="define-table-object"}

In Exposed, a database table is represented by an object inherited from the `Table` class.
To define the table object, follow the steps below.

<procedure id="define-table-object-procedure">
<step>

In the <path>app/src/main/kotlin/org/example/</path> folder, create a new <path>Task.kt</path> file.

</step>
<step>

Open <path>Task.kt</path> and add the following table definition:

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/Task.kt"}

In the `Table` constructor, passing the name `tasks` configures a custom
name for the table. Keep in mind that if no custom name is specified, Exposed will generate
one from the class name, which might lead to unexpected results.

Within the `Tasks` object, the following columns are defined:

- `id` of type `Int` is defined with the `integer()` function.
  The `autoIncrement()` function indicates that this column is
  an auto-incrementing integer, typically used for primary keys.
- `title` and `description` of type `String` are defined
  with the `varchar()` function.
- `isCompleted` of type `Boolean` is defined with the
  `bool()` function. Using the `default()` function, you configure the
  default value to `false`.

At this point, you have defined a table with columns, which essentially creates the
blueprint for the `Tasks` table.

To now create and populate the table within the database, you need to open a transaction.

</step>
</procedure>

## Create and query a table {id="create-and-query-table"}

With Exposed’s DSL API, you can interact with a database using a type-safe syntax similar to SQL.
Before you start executing database operations, you must open a
<emphasis>transaction</emphasis>.

A [transaction](Transactions.md) is represented by an instance of the `Transaction` class, within which you
can define and manipulate data using its lambda function. Exposed will automatically manage the opening
and closing of the transaction in the background, ensuring seamless operation.
{id="transaction-definition"}

<procedure id="create-and-query-table-procedure">
<step>

Navigate back to the <path>App.kt</path> file and add the following transaction function:

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-lines="4-20,26-44,57-59"}

First, you create the tasks table using the `SchemaUtils.create()` function.
The `SchemaUtils` object holds utility methods for creating, altering, and
dropping database objects.

Once the table has been created, you use the `Table` extension function `insert()`
to add new `Task` records:

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-lines="28-37"}

Within the `insert {}` block, you set the values for
each column by using the `it` parameter. Exposed translates the functions
into the following SQL queries:

```sql
INSERT INTO TASKS ("name", DESCRIPTION)
       VALUES ('Learn Exposed', 'Go through the Get started with Exposed tutorial')
INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED)
       VALUES ('Read The Hobbit', 'Read the first two chapters of The Hobbit', TRUE)
```
{noinject="true"}

Because the `insert()` function returns an `InsertStatement`, by using
the `get()` method after the `insert` operation you retrieve the
autoincremented `id` value of the newly added row.

With the `select()` extension function you then create a query to count the number
of rows and to retrieve the `isCompleted` value for each row in the table:

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-lines="41-43"}

The `groupBy()` function groups the results of the query by the `isCompleted`
column, which means it will aggregate the rows based on whether they are completed or not.
The expected SQL query looks like this:

```sql
SELECT COUNT(TASKS.ID), TASKS.COMPLETED FROM TASKS GROUP BY TASKS.COMPLETED
```
{noinject="true"}

It is important to note that the query will not be executed until you call a function that
iterates through the result, such as `forEach()`. In this example, for each group
you print out the `isCompleted` status and the corresponding count of tasks.

</step>
</procedure>

Before you test the code, it would be handy to be able to inspect the SQL statements
and queries Exposed sends to the database. For this, you need to add a logger.

## Enable logging {id="enable-logging"}

At the beginning of your transaction block, add the following code to enable SQL query logging:

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-lines="20-24,58"}

## Run the application {id="run-in-intellij-idea"}

<include from="lib.topic" element-id="intellij_idea_start_application"/>

The application will start in the **Run** tool window at the
bottom of the IDE. There, you will be able to see the SQL logs along with the printed results:

```shell
SQL: CREATE TABLE IF NOT EXISTS TASKS (ID INT AUTO_INCREMENT NOT NULL, "name" VARCHAR(128) NOT NULL, DESCRIPTION VARCHAR(128) NOT NULL, COMPLETED BOOLEAN DEFAULT FALSE NOT NULL)
SQL: INSERT INTO TASKS ("name", DESCRIPTION) VALUES ('Learn Exposed', 'Go through the Get started with Exposed tutorial')
SQL: INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED) VALUES ('Read The Hobbit', 'Read the first two chapters of The Hobbit', TRUE)
Created new tasks with ids 1 and 2.
SQL: SELECT COUNT(TASKS.ID), TASKS.COMPLETED FROM TASKS GROUP BY TASKS.COMPLETED
false: 1 
true: 1 
SQL: SELECT TASKS.ID, TASKS."name", TASKS.DESCRIPTION, TASKS.COMPLETED FROM TASKS
Remaining tasks: [org.example.Tasks.id=1, org.example.Tasks.name=Learn Exposed, org.example.Tasks.description=Go through the Get started with Exposed tutorial, org.example.Tasks.completed=false, org.example.Tasks.id=2, org.example.Tasks.name=Read The Hobbit, org.example.Tasks.description=Read the first two chapters of The Hobbit, org.example.Tasks.completed=true]

```

## Update and delete a task {id="update-and-delete-task"}

Let’s extend the app’s functionality by updating and deleting the same task.

<procedure id="update-and-delete-task-procedure">
<step>

In the same `transaction()` function, add the following code to your implementation:

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-lines="20,24,44-58"}

Here's the breakdown:

In the `Tasks.update()` function, you specify the condition to find the task
with `id` equal to the one of the previously inserted task. If the condition is met,
the `isCompleted` field of the found task is set to `true`.

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-lines="46-49"}

Unlike the `insert()` function, `update()` returns the number of updated rows.
To then retrieve the updated task, you use the `select()` function
with the `where` condition to only select the tasks with `id`
equal to `taskId`.

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-symbol="updatedTask"}

Using the `single()` extension function
initiates the statement and retrieves the first result found.

The `deleteWhere()` function, on the other hand, deletes the task with the specified condition.

```kotlin
```
{src="get-started-with-exposed/src/main/kotlin/org/example/App.kt" include-lines="55"}

Similarly to `update()`, it returns the number of rows that have been deleted.

</step>
<step>
<include from="lib.topic" element-id="intellij_idea_restart_application"/>

You should now see the following result:

```console
SQL: CREATE TABLE IF NOT EXISTS TASKS (ID INT AUTO_INCREMENT NOT NULL, "name" VARCHAR(128) NOT NULL, DESCRIPTION VARCHAR(128) NOT NULL, COMPLETED BOOLEAN DEFAULT FALSE NOT NULL)
SQL: INSERT INTO TASKS ("name", DESCRIPTION) VALUES ('Learn Exposed', 'Go through the Get started with Exposed tutorial')
SQL: INSERT INTO TASKS ("name", DESCRIPTION, COMPLETED) VALUES ('Read The Hobbit', 'Read the first two chapters of The Hobbit', TRUE)
Created new tasks with ids 1 and 2.
SQL: SELECT COUNT(TASKS.ID), TASKS.COMPLETED FROM TASKS GROUP BY TASKS.COMPLETED
false: 1 
true: 1 
SQL: SELECT TASKS.ID, TASKS."name", TASKS.DESCRIPTION, TASKS.COMPLETED FROM TASKS
Remaining tasks: [org.example.Tasks.id=1, org.example.Tasks.name=Learn Exposed, org.example.Tasks.description=Go through the Get started with Exposed tutorial, org.example.Tasks.completed=false, org.example.Tasks.id=2, org.example.Tasks.name=Read The Hobbit, org.example.Tasks.description=Read the first two chapters of The Hobbit, org.example.Tasks.completed=true]
SQL: UPDATE TASKS SET COMPLETED=TRUE WHERE TASKS.ID = 1
SQL: SELECT TASKS.COMPLETED FROM TASKS WHERE TASKS.ID = 1
Updated task details: org.example.Tasks.completed=true
SQL: DELETE FROM TASKS WHERE TASKS.ID = 2
SQL: SELECT TASKS.ID, TASKS."name", TASKS.DESCRIPTION, TASKS.COMPLETED FROM TASKS
Remaining tasks: [org.example.Tasks.id=1, org.example.Tasks.name=Learn Exposed, org.example.Tasks.description=Go through the Get started with Exposed tutorial, org.example.Tasks.completed=true]

```

</step>
</procedure>

> If a second `transaction {}` is opened after the first one,
> you will find that the table and its data has been lost
> even though the app hasn't stopped. This is [expected behavior in H2 databases](https://www.h2database.com/html/features.html#in_memory_databases)
> when managing connections and transactions.
>
> To keep the database open, add `;DB_CLOSE_DELAY=-1` to the database URL:
>
> ```kotlin
> Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
> ```
>
{style="tip" id="second-transaction-behaviour-tip"}

## Next steps {id="next-steps"}

Great job! You have now implemented a simple console application that uses Exposed to fetch and modify
task data from an in-memory database. Now that you’ve covered the basics, you are ready to
[dive deep into the DSL API](DSL-CRUD-operations.topic).
