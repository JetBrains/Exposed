# Hashing data

<show-structure for="chapter,procedure" depth="2"/>
<var name="artifact_name" value="exposed-crypt"/>

<tldr>

**Required dependencies**: `org.jetbrains.exposed:%artifact_name%`

<include from="lib.topic" element-id="jdbc-supported"/>
<include from="lib.topic" element-id="r2dbc-supported"/>
</tldr>

Exposed supports one-way hashing for sensitive data such as passwords through the `exposed-crypt` module.

Unlike encryption, hashing does not allow the original value to be recovered. Instead, you verify a plaintext value
against the stored hash.

## Add dependencies {id="add-dependency"}

To use hashing with Exposed, add the `%artifact_name%` module to your build script:

<include from="lib.topic" element-id="add-dependency"/>

## Basic usage

To create a hashed column, apply the `.hashed()` function to a character column:

```kotlin
object Users : IntIdTable() {
    val password = text("password").hashed()
}
```

The `.hashed()` function changes the Kotlin type of the column from `String` to `Hashed`.

## Supported algorithms

The `.hashed()` function uses `BCryptHasher` by default. You can change the default hasher by choosing one of the
supported `Hasher` implementations:

| Hasher         | Algorithm |
|----------------|-----------|
| `BCryptHasher` | `bcrypt`  |
| `Argon2Hasher` | `Argon2`  |
| `Pbkdf2Hasher` | `PBKDF2`  |
| `SCryptHasher` | `scrypt`  |

To use another hashing algorithm or customize its parameters,
create a `Hasher` and pass it to the `.hashed()` function:

```kotlin
val hasher = BCryptHasher()

object Users : IntIdTable() {
    val password = text("password").hashed(hasher)
}

```

## Configure a hasher

Each hasher provides parameters for configuring the amount of work required to generate and verify a hash. For example,
you can configure the strength used by `BCryptHasher`:

```kotlin
val hasher = BCryptHasher( strength = 12 )
```

For `Argon2Hasher`, you can configure parameters such as memory usage, iterations, and parallelism:

```kotlin
val hasher = Argon2Hasher(
    memory = 19_456,
    iterations = 2,
    parallelism = 1
)
```

`Pbkdf2Hasher` also lets you select the pseudorandom function:

```kotlin
val hasher = Pbkdf2Hasher(
    algorithm = Pbkdf2Algorithm.SHA256
)
```

## Use a Spring Security password encoder

If your application already uses a Spring Security `PasswordEncoder`, wrap it with `PasswordEncoderHasher` to adapt it to
the `Hasher` type:

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
Users.insert {
    it[password] = hasher.hash("s3cret")
}
```

The `.hash()` function returns a `Hashed` value containing the encoded hash.

> Hashing is salted, so hashing the same plaintext value more than once can produce different encoded values.
>
{style="note"}

## Verify a value

To verify a plaintext value, use the `.matches()` function on the stored `Hashed` value:

```kotlin
val user = Users.selectAll().where {
    Users.email eq email
}.single()

if (user[Users.password].matches(submittedPassword)) {
/* ... */ 
}
```

The `.matches()` function returns `true` if the plaintext value matches the stored hash.


