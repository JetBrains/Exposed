##SQL Server dialect  

### Limitations

* Batch insert actually inserts rows one by one, because SQLServer do not return generated keys.  
* SQL Server looses precision when it stores timestamps.

### Running tests locally

* Run SQL Server locally, e.g. with Docker image from 
https://hub.docker.com/r/microsoft/mssql-server-linux/
* Uncomment `+ ",sqlserver"` line in `src/test/kotlin/org/jetbrains/exposed/sql/tests/DatabaseTestsBase.kt`

