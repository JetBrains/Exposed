# Hashing data

<show-structure for="chapter,procedure" depth="2"/>
<var name="artifact_name" value="exposed-crypt"/>
<var name="example_name" value="exposed-hashing-data"/>

<tldr>
<include from="lib.topic" element-id="required_dependency"/>
<include from="lib.topic" element-id="code_example"/>
<include from="lib.topic" element-id="jdbc-supported"/>
<include from="lib.topic" element-id="r2dbc-supported"/>
</tldr>

Exposed supports one-way hashing for sensitive data such as passwords through the `exposed-crypt` module.

Unlike encryption, hashing does not allow the original value to be recovered. Instead, you verify a plaintext value
against the stored hash.

## Add dependencies {id="add-dependencies"}

To use hashing with Exposed, add the `%artifact_name%` module to your build script:

<include from="lib.topic" element-id="add-dependency"/>

Optionally, to use `scrypt` and `Argon2` hashing, add the [Bouncy Castle](https://www.bouncycastle.org/) library as a 
runtime dependency:

<var name="external_artifact_groupId" value="org.bouncycastle"/>
<var name="external_artifact_name" value="bcprov-jdk18on"/>
<var name="external_artifact_version" value="%bouncy_castle_version%"/>
<include from="lib.topic" element-id="add-external-runtime-dependency"/>


## Basic usage

To create a hashed column, apply the [`.hashed()`](https://jetbrains.github.io/Exposed/api/exposed-crypt/org.jetbrains.exposed.v1.crypt/hashed.html)
function to a character column:

```kotlin
object Users : IntIdTable() {
    val password = text("password").hashed()
}
```

The `.hashed()` function changes the Kotlin type of the column from `String` to `Hashed`.

## Supported algorithms

The `.hashed()` function uses `BCryptHasher` by default. You can change the default hasher by choosing one of the
supported `Hasher` implementations:

| Hasher                                                                                                                            | Algorithm |
|-----------------------------------------------------------------------------------------------------------------------------------|-----------|
| [`BCryptHasher`](https://jetbrains.github.io/Exposed/api/exposed-crypt/org.jetbrains.exposed.v1.crypt/-b-crypt-hasher/index.html) | `bcrypt`  |
| [`Argon2Hasher`](https://jetbrains.github.io/Exposed/api/exposed-crypt/org.jetbrains.exposed.v1.crypt/-argon2-hasher/index.html)  | `Argon2`  |
| [`Pbkdf2Hasher`](https://jetbrains.github.io/Exposed/api/exposed-crypt/org.jetbrains.exposed.v1.crypt/-pbkdf2-hasher/index.html)  | `PBKDF2`  |
| [`SCryptHasher`](https://jetbrains.github.io/Exposed/api/exposed-crypt/org.jetbrains.exposed.v1.crypt/-s-crypt-hasher/index.html) | `scrypt`  |

To use another hashing algorithm or customize its parameters,
create a `Hasher` and pass it to the `.hashed()` function:

```kotlin
```
{src="exposed-hashing-data/src/main/kotlin/org/example/tables/UsersTable.kt" include-symbol="hasher, Users"}

## Configure a hasher

Each hasher provides parameters for configuring the amount of work required to generate and verify a hash. For example,
you can configure the strength used by `BCryptHasher`:

```kotlin
```
{src="exposed-hashing-data/src/main/kotlin/org/example/tables/UsersTable.kt" include-symbol="bCryptHasher"}

For `Argon2Hasher`, you can configure parameters such as memory usage, iterations, and parallelism:

```kotlin
```
{src="exposed-hashing-data/src/main/kotlin/org/example/tables/UsersTable.kt" include-symbol="argon2Hasher"}

> `Argon2Hasher` requires the [Bouncy Castle](https://www.bouncycastle.org/) library as a runtime dependency. For more
> information, see [](#add-dependencies).
> 
{style="note"}

`Pbkdf2Hasher` also lets you select the pseudorandom function:

```kotlin
```
{src="exposed-hashing-data/src/main/kotlin/org/example/tables/UsersTable.kt" include-symbol="pbkdf2Hasher"}

> For a complete list of the available configuration options, refer to [the API documentation](https://jetbrains.github.io/Exposed/api/exposed-crypt/org.jetbrains.exposed.v1.crypt/index.html).
> 
{style="tip"}

## Use a Spring Security password encoder

If your application already uses a [Spring Security `PasswordEncoder`](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#authentication-password-storage),
wrap it with [`PasswordEncoderHasher`](https://jetbrains.github.io/Exposed/api/exposed-crypt/org.jetbrains.exposed.v1.crypt/-password-encoder-hasher/index.html) to
adapt it to the `Hasher` type:

```kotlin
val passwordEncoder = MyPasswordEncoder()
val hasher = PasswordEncoderHasher(passwordEncoder)

object Users : IntIdTable() { 
    val password = text("password").hashed(hasher)
}
```

`PasswordEncoderHasher` delegates hashing and verification to the supplied `PasswordEncoder` while exposing the standard
Exposed `Hasher` API.

## Hash and store a value

Use the configured `Hasher` to hash a plaintext value before storing it:

```kotlin
```
{src="exposed-hashing-data/src/main/kotlin/org/example/App.kt" include-lines="28,30-31"}

The `.hash()` function returns a `Hashed` value containing the encoded hash.

> Hashing is [_salted_](https://en.wikipedia.org/wiki/Salt_(cryptography)), so hashing the same plaintext value more than
> once can produce different encoded values.
>
{style="note"}

## Verify a value

To verify a plaintext value, use the `.matches()` function on the stored `Hashed` value:

```kotlin
```
{src="exposed-hashing-data/src/main/kotlin/org/example/App.kt" include-lines="32-38"}

The `.matches()` function returns `true` if the plaintext value matches the stored hash.


