##SQL Server dialect  

### Limitations

* Batch insert actually inserts rows one by one, because SQLServer do not return generated keys.  
* SQL Server looses precision when it stores timestamps.

### Running tests locally

* Run SQL Server locally, e.g. with Docker image with command like
docker run -e 'ACCEPT_EULA=Y' -e 'SA_PASSWORD=yourStrong(!)Password' -p 1433:1433 -d microsoft/mssql-server-linux
* Run tests with `-Dexposed.test.dialects=sqlserver`

