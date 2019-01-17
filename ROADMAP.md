# Exposed Roadmap

* Replace joda-time as date-time library with plugable approach which will allow to chose among joda/java8 time/threeten and also fix all the issues tagged with [datetime](https://github.com/JetBrains/Exposed/issues?q=is%3Aissue+is%3Aopen+label%3Adatetime). (see [date-split-modules](https://github.com/JetBrains/Exposed/tree/date-split-modules)) branch
* Split Exposed to modules as core/dsl, dao and per-database modules
* Support coroutines and eliminate thread-local based transaction manager
* Refactor test to cover different jdbc-drivers/database versions (with docker-compose or test containers)
* Move from jdbc drivers to something more asynchronous ([R2DBC](https://r2dbc.io/)\/[ADBA](https://github.com/oracle/oracle-db-examples/tree/master/java/AoJ))
