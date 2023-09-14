## Oracle Dialect

### Limitations

* Only Oracle 12 supported (limit with `FETCH ... ROWS ONLY`)
* `autoinc` columns supported by sequences, need an argument with sequence name
* `insert` with `select` with `limit` not supported (`DMLTests.testInsertSelect01`)

### Running tests locally with Gradle

* Run `testOracle` gradle task

### Running tests locally with Docker

* Run Oracle locally, e.g. with `gvenzl/oracle-xe:18-slim-faststart` Docker image or use `docker-compose -f docker-compose-oracle.yml up`
* Run tests with `-Dexposed.test.dialects=oracle`,
  (optionally you may need to provide `-Dexposed.test.oracle.host=_YOUR_DOCKER_HOST_ -exposed.test.oracle.port=_ORACLE_EXPOSED_PORT_`)
