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

To display a specific source file in a topic, use the `code-block` element with the `src` attribute as follows:

### XML

````xml
<code-block lang="kotlin" src="exposed-dao/src/main/kotlin/org/example/StarWarsFilms.kt" />
````

### Markdown

````
```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/StarWarsFilms.kt"}
````

Use the following additional attributes:
- Use the `include-symbol` attribute to display only a specific function from the source file.
- Use the `include-lines` attribute to display specific lines from the source file.

