##SQL Server dialect  

* Batch insert is not supported, because SQLServer do not return generated keys.  

### Running tests locally

* Run SQL Server locally, e.g. with Docker image from 
https://hub.docker.com/r/microsoft/mssql-server-linux/
* Uncomment `+ ",sqlserver"` line in `src/test/kotlin/org/jetbrains/exposed/sql/tests/DatabaseTestsBase.kt`

