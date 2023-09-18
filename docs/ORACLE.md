## Oracle Dialect

### Limitations

* Only Oracle 12 supported (limit with `FETCH ... ROWS ONLY`)
* `autoinc` columns supported by sequences, need an argument with sequence name
* `insert` with `select` with `limit` not supported (`DMLTests.testInsertSelect01`)

### Running tests locally with Gradle

* Run `testOracle` gradle task

### Running tests locally with Docker

* Run Oracle locally with gradle task `oracleComposeUp`
* Run tests with `testOracle` gradle task
