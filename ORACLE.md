## Oracle Dialect

### Limitations

* Only Oracle 12 supported (limit with `FETCH ... ROWS ONLY`)
* `autoinc` columns supported by sequences, need an argument with sequence name
* `insert` with `select` with `limit` not supported (`DMLTests.testInsertSelect01`)

### Running tests locally

* Run Oracle locally, e.g. with `sath89/oracle-12c` Docker image
* Run tests with `-Dexposed.test.dialects=oracle`
