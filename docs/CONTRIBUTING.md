# How to contribute

We're delighted that you're considering contributing to Exposed!

There are multiple ways you can contribute:

* Code
* Documentation
* Community Support
* Issues and Feature Requests

This project and the corresponding community is governed by
the [JetBrains Open Source and Community Code of Conduct](https://confluence.jetbrains.com/display/ALL/JetBrains+Open+Source+and+Community+Code+of+Conduct).
Independently of how you'd like to contribute, please make sure you read and comply with it.

### Code

#### Pull Requests

Contributions are made using Github [pull requests](https://help.github.com/en/articles/about-pull-requests):

1. Fork the Exposed repository, because imitation is the sincerest form of flattery.
2. Clone your fork to your local machine.
3. Create a new branch for your changes.
4. [Create](https://github.com/JetBrains/Exposed/compare) a new PR with a request to merge to the **master** branch.
5. Ensure that the description is clear and refers to an existing ticket/bug if applicable, prefixing the description with
   EXPOSED-{NUM}, where {NUM} refers to the YouTrack issue.
6. When contributing a new feature, provide motivation and use-cases describing why
   the feature not only provides value to Exposed, but also why it would make sense to be part of the Exposed framework itself.
7. If the contribution requires updates to documentation (be it updating existing contents or creating new one), please
   file a new ticket on [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED).
8. Make sure any code contributed is covered by tests and no existing tests are broken. We use Docker containers to run tests.

#### Style Guides

A few things to remember:

* Your code should conform to the official [Kotlin code style guide](https://kotlinlang.org/docs/reference/coding-conventions.html)
  except that star imports should be always enabled.
  (ensure Preferences | Editor | Code Style | Kotlin, tab **Imports**, both `Use import with '*'` should be checked).
* Every new source file should have a copyright header.
* Every public API (including functions, classes, objects and so on) should be documented,
  every parameter, property, return types and exceptions should be described properly.

#### Commit messages

* Commit messages should be written in English.
* Their title should be prefixed according to [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary).
* They should be written in present tense using imperative mood ("Fix" instead of "Fixes", "Improve" instead of "Improved").
  See [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/).
* When applicable, prefix the commit message with EXPOSED-{NUM} where {NUM} represents the YouTrack issue number.
* Add the related bug reference to a commit message (bug number after a hash character between round braces).

### Documentation

There are multiple ways in which you can contribute to Exposed docs:

- Create an issue in [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED).
- Submit a pull request with proposed changes.

### Community Support

If you'd like to help others, please join our Exposed [channel](https://kotlinlang.slack.com/archives/C0CG7E0A1) on the Kotlin Slack workspace and
help out. It's also a great way to learn!

### Issues and Feature Requests

If you encounter a bug or have an idea for a new feature, please submit it to us through [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED),
our issue tracker.

Before submitting an issue or feature request, please search YouTrack's existing issues to avoid reporting duplicates.

When submitting an issue or feature request, please provide as much detail as possible, including a clear and concise description of the problem or
desired functionality, steps to reproduce the issue, and any relevant code snippets or error messages.

Thank you for your cooperation and for helping to improve Exposed.
