## Oracle Dialect

### Limitations

* Only Oracle 12 supported (limit with `FETCH ... ROWS ONLY`)
* `IF NOT EXISTS` not supported, so `create` will fail if tables already exist
* `autoinc` columns supported by sequences, need an argument with sequence name
* `insert` with `select` with `limit` not supported (`DMLTests.testInsertSelect01`)

### Running tests locally

* Run Oracle locally, e.g. with `sath89/oracle-12c` Docker image
* Download `ojdbc6.jar` and put into local Maven repository
* Uncomment `testCompile 'com.oracle:ojdbc6:12.1.0.2'` in `build.gradle`
* Run tests with `-Dexposed.test.dialects=oracle`
