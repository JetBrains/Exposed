# Exposed Docs code examples

The `snippets` folder contains a Gradle project with runnable code examples that show how to work with the Exposed library.
Code from these examples is referenced in corresponding documentation sections.

## Run samples

Each example has its own `README` file with instructions on how to run it.
To run an example, you can use a **run** Gradle task that depends on an example location. 
For example, to run the `exposed-dao` example, open a new terminal window from the `snippets` folder and execute the following command:

```bash
./gradlew :exposed-dao:run
```

Wait until IntelliJ IDEA builds and runs an example.

## Reference code snippets

### Reference files and symbols

To display a specific source file in a topic, use the [`code-block`](https://www.jetbrains.com/help/writerside/semantic-markup-reference.html#code-block)
element with the `src` attribute. Whenever possible, reference the entire files or use the `include-symbol` attribute 
to specify a class, method, or another symbol from the source file to include in the code block.


#### XML

````xml
<!-- Include all file contents -->
<code-block lang="kotlin" src="exposed-dao/src/main/kotlin/org/example/StarWarsFilms.kt" />

<!-- Include specific symbol/s -->
<code-block lang="kotlin" src="exposed-dao/src/main/kotlin/org/example/StarWarsFilms.kt" include-symbol="MAX_VARCHAR_LENGTH, StarWarsFilmsTable" />
````

#### Markdown

````
```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/StarWarsFilms.kt"}
````

### Reference by line numbers

In cases where the example code is not assigned or is commented out, use the `include-lines` attribute to specify the line
numbers from the source file to include in the code block:

#### XML

```xml
<!-- Include specific lines -->
<code-block lang="sql" src="exposed-dao/src/main/kotlin/org/example/StarWarsFilms.kt" include-lines="9-13" />
```
#### Markdown

````
```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/StarWarsFilms.kt" include-lines="9-13"}
````

When using this approach, be sure to document it for future reference by adding a note about the explicitly referenced
lines, as shown in the example below:

```kotlin
/*
    ...

    Important: The SQL query is referenced by line number in `TOPIC_NAME.topic`.
    If you add, remove, or modify any lines before the SELECT statement, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.

    CREATE TABLE IF NOT EXISTS STARWARSFILMS
    (ID INT AUTO_INCREMENT PRIMARY KEY,
    SEQUEL_ID INT NOT NULL,
    "name" VARCHAR(50) NOT NULL,
    DIRECTOR VARCHAR(50) NOT NULL);
 */
object StarWarsFilmsTable : IntIdTable() {
    //...
}
```

