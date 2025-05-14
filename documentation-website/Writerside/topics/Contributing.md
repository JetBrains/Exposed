<show-structure for="chapter,procedure" depth="3"/>

# Contributing to Exposed

We're delighted that you're considering contributing to Exposed!

There are multiple ways you can contribute:

* Issues and Feature Requests
* Documentation
* Code
* Community Support

This project and the corresponding community is governed by
the [JetBrains Open Source and Community Code of Conduct](https://confluence.jetbrains.com/display/ALL/JetBrains+Open+Source+and+Community+Code+of+Conduct).
Independently of how you'd like to contribute, please make sure you read and comply with it.

## Issues and Feature Requests

If you encounter a bug or have an idea for a new feature, please submit it to us through [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED),
our issue tracker. While issues are visible publicly, either creating a new issue or commenting on an existing one does require logging in to YouTrack.

Before submitting an issue or feature request, search YouTrack's [existing issues](https://youtrack.jetbrains.com/issues/EXPOSED) to avoid reporting duplicates.

When submitting an issue or feature request, provide as much detail as possible, including a clear and concise description of the problem or
desired functionality, steps to reproduce the issue, and any relevant code snippets or error messages.

## Documentation

There are multiple ways in which you can contribute to Exposed documentation:

- Create an issue in [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED).
- Submit a [pull request](#pull-requests) containing your proposed changes.
  Ensure that these modifications are applied directly within the `documentation-website` directory only, **not** to files in the `docs` folder.

## Code

### Pull Requests

Contributions are made using GitHub [pull requests](https://help.github.com/en/articles/about-pull-requests):

1. Fork the [Exposed repository](https://github.com/JetBrains/Exposed), because imitation is the sincerest form of flattery.
2. Clone your fork to your local machine.
3. Create a new branch for your changes.
4. [Create](https://github.com/JetBrains/Exposed/compare) a new PR with a request to merge to the **main** branch.
5. Ensure that the PR title is clear and refers to an [existing ticket/bug](https://youtrack.jetbrains.com/issues/EXPOSED) if applicable,
   prefixing the title with both a [conventional commit](https://www.conventionalcommits.org/en/v1.0.0/#summary)
   and EXPOSED-&lcub;NUM&rcub;, where &lcub;NUM&rcub; refers to the YouTrack issue code.
   For more details about the suggested format, see [commit messages](#commit-messages).
6. When contributing a new feature, provide motivation and use-cases describing why
   the feature not only provides value to Exposed, but also why it would make sense to be part of the Exposed framework itself.
   Complete as many sections of the PR template description as applicable.
7. If the contribution requires updates to documentation (be it updating existing content or creating new content), either do so in the same PR or
   file a new ticket on [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED).
   Any new public API objects should be documented with a [KDoc](https://kotlinlang.org/docs/kotlin-doc.html) in the same PR.
8. If the contribution makes any breaking changes, ensure that this is properly denoted in 3 ways:
   - In the PR (and commit) title using the appropriate [conventional commit](https://www.conventionalcommits.org/en/v1.0.0/#commit-message-with--to-draw-attention-to-breaking-change)
   - By ticking the relevant checkbox in the PR template description
   - By adding relevant details to [Breaking Changes](http://jetbrains.github.io/Exposed/breaking-changes.html)
9. Make sure any code contributed is covered by tests and no existing tests are broken. We use Docker containers to run tests.
10. Execute the `detekt` task in Gradle to perform code style validation. 
11. Finally, make sure to run the `apiCheck` Gradle task. If it's not successful, run the `apiDump` Gradle task. Further information can be
   found [here](https://github.com/Kotlin/binary-compatibility-validator).

### Style Guides

A few things to remember:

* Your code should conform to the official [Kotlin code style guide](https://kotlinlang.org/docs/reference/coding-conventions.html)
  except that star imports should always be enabled.
  (ensure Preferences | Editor | Code Style | Kotlin, tab **Imports**, both `Use import with '*'` should be checked).
* Every new source file should have a copyright header.
* Every public API (including functions, classes, objects and so on) should be documented,
  every parameter, property, return types, and exceptions should be described properly.

Test functions:

* Begin each test function name with the word `test`.
* Employ camelCase for test function names, such as `testInsertEmojisWithInvalidLength`. 
* Refrain from using names enclosed in backticks for test functions, because `KDocs` cannot reference function names that contain spaces.
* In the definition of test functions, use a block body instead of an assignment operator. 
  For example, do write `fun testMyTest() { withDb{} }`, and avoid writing `fun testMyTest() = withDb{}`.

### Commit messages

* Commit messages should be written in English.
* Their title should be prefixed according to [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary).
* They should be written in present tense using imperative mood ("Fix" instead of "Fixes", "Improve" instead of "Improved").
  See [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/).
* When applicable, prefix the commit message with EXPOSED-&lcub;NUM&rcub; where &lcub;NUM&rcub; refers to the
  [YouTrack issue](https://youtrack.jetbrains.com/issues/EXPOSED) code.
* An example could be: `fix: EXPOSED-123 Fix a specific bug`

### Test Coverage

Exposed project has configures test coverage tasks.

To generate a test coverage report, run:
```shell
./gradlew :exposed-tests:koverHtmlReport
./gradlew :exposed-r2dbc-tests:koverHtmlReport
```

## Community Support

If you'd like to help others, please join our Exposed [channel](https://kotlinlang.slack.com/archives/C0CG7E0A1) on the Kotlin Slack workspace and
help out. It's also a great way to learn!

Thank you for your cooperation and for helping to improve Exposed.
