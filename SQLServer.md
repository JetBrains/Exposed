## SQL Server dialect  

### Running tests locally with Gradle
* Set `dialect` in gradle.properties to 'sqlserver'
* Run `exposedDialectTestWithDocker` gradle task  

### Running tests locally with Docker
* Run SQL Server locally, e.g. with Docker image with command like
`docker run -e 'ACCEPT_EULA=Y' -e 'SA_PASSWORD=yourStrong(!)Password' -p 1433:1433 -d microsoft/mssql-server-linux`
or use `docker-compose -f docker-compose-sqlserver.yml up` 

* Run tests with `-Dexposed.test.dialects=sqlserver`, 
(optionally you may need to provide `-Dexposed.test.sqlserver.host=_YOUR_DOCKER_HOST_ -exposed.test.sqlserver.port=_SQLSERVER_SERVER_EXPOSED_PORT_`)

