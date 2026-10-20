# Exposed Spring Data in a GraalVM native image

A small Spring Boot 4.1 application using `exposed-spring-data-starter` that is built and tested as a GraalVM native image. It
covers what the module registers for Spring AOT: repository beans, CRUD, derived and paged queries, DTO and interface projections, a
dynamic projection, `query { }` default methods, a `Persistable` entity, auditing with named `AuditorAware` / `DateTimeProvider` beans
and an entity callback.

The sample is **opt-in**: it is a standalone Gradle build that is not part of the Exposed build or CI. It consumes Exposed from this
checkout through a composite build (`includeBuild("../..")` in `settings.gradle.kts`), so it always runs against the current sources.

## Requirements

- Nothing to install beyond a JDK 17+ to run Gradle: the JDK 17 compilation toolchain and GraalVM Community for JDK 25 are Gradle
  toolchains, detected locally or downloaded through the foojay resolver (`settings.gradle.kts`). The first native build without a
  local GraalVM Community 25 downloads it (about 340 MB) into `~/.gradle/jdks`. The toolchain vendor is fixed to GraalVM
  (`JvmVendorSpec.GRAAL_VM`), because without it foojay would pick Mandrel first.
- To use another native-image JDK 25 (or newer) instead, e.g. Oracle GraalVM, Mandrel or Liberica NIK, set `GRAALVM_HOME`; it takes
  precedence over the toolchain.

Gradle extracts provisioned toolchains without symbolic links ([gradle/gradle#28583](https://github.com/gradle/gradle/issues/28583)),
which turns `bin/native-image` of a downloaded GraalVM into an empty file: `nativeCompile` then "succeeds" without writing an
executable and `nativeRun` fails with `property 'image' specifies file '.../nativeCompile/exposed-spring-data-native' which doesn't
exist`. Before every native image build, `RestoreNativeImageLinks` in `build.gradle.kts` restores the links of the GraalVM 25
distribution (`bin/native-image`, `bin/native-image-configure`, `bin/native-image-utils`, `lib/svm/bin/native-image-configure`,
`LICENSE_NATIVEIMAGE.txt`). It only changes JDKs below `~/.gradle/jdks`, and only links that are empty files with an existing target;
locally installed JDKs and `GRAALVM_HOME` are never touched. The build is compatible with the configuration cache.

## Running

```shell
cd samples/exposed-spring-data-native

# JVM: regular Spring context
./gradlew test

# native image: processTestAot, nativeTestCompile, then runs the JUnit tests in the native executable
./gradlew nativeTest

# the application itself: builds and starts the native executable
./gradlew nativeRun
# or
./gradlew nativeCompile
./build/native/nativeCompile/exposed-spring-data-native

# with an installed GraalVM instead of the toolchain
GRAALVM_HOME=/path/to/graalvm ./gradlew nativeRun
```

Without `GRAALVM_HOME` (and without the toolchain configuration in `build.gradle.kts`), Native Build Tools falls back to `JAVA_HOME`
and fails with `Cannot query the value of property 'javaLauncher' because it has no value available` if that isn't set either.

The first `nativeTest` also builds Exposed and takes a few minutes; most of it is the native image build.

## What the application needs to declare itself

Everything the repositories need is registered by `exposed-spring-data` during AOT processing. The only exception is a projection type
that is only known when a method is called (a `Class<T>` argument or a `query<T> { }` type that doesn't appear in the method's return
type): `CustomerAge` is used like that and is registered with `@RegisterReflectionForBinding` on `NativeSampleApplication`.

Removing that annotation makes the dynamic projection fail in the native image with
`KotlinReflectionInternalError: Could not compute caller for function: fun <init>(kotlin.Int): CustomerAge`.

## Findings

Verified with Oracle GraalVM 25.0.4 through `GRAALVM_HOME` and with GraalVM CE 25.4.4.1.1 (Java 25.0.4.1) downloaded as a Gradle
toolchain (macOS, aarch64), Spring Boot 4.1.0, Native Build Tools 1.1.1: all 12 tests pass in the native image (`nativeTest`), and
`nativeRun` starts the native application, which creates its schema.

Gaps in `exposed-spring-data` found by this sample (fixed in the module's `ExposedSpringDataRuntimeHints`):

- Spring Data reads the bean properties of Kotlin classes with kotlin-reflect (`KotlinBeanInfoFactory`). For the Kotlin factory bean of
  the repositories this resolves every type of its hierarchy by name, and failed with
  `UnsupportedOperationException: Type not found: class org.springframework.data.repository.query.QueryLookupStrategy$Key`. The module
  now registers the types in the signatures of its Kotlin infrastructure beans.
- Entity callbacks are looked up on the callback interface by reflection; without hints every repository call failed with
  `AfterConvertCallback does not define a callback method accepting Customer and 1 additional arguments`. The module now registers the
  methods of its callback interfaces.

Exposed core: no gaps showed up for this setup (Exposed JDBC on H2 through the starter's auto-configured `SpringTransactionManager`),
so the sample needs no workaround hints. The `ServiceLoader` lookups of Exposed (`DatabaseConnectionAutoRegistration`,
`TransactionsHolder`, `EntityIDFactory`) work because `native-image` includes `META-INF/services` providers. Not covered: other
databases and dialects, the DAO API and the other extension modules (e.g. `exposed-json`).

Spring Data commons logs an INFO message that it couldn't read class metadata for the `CustomerView` interface projection in the native
image and that input property calculation might fail. It falls back to reflection order; the interface projection test passes.
