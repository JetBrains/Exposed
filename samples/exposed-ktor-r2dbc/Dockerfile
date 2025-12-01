FROM eclipse-temurin:17-jdk
EXPOSE 8080:8080
RUN mkdir /app
COPY ./build/libs/*-all.jar /app/exposed-ktor-r2dbc.jar
ENTRYPOINT ["java","-jar","/app/exposed-ktor-r2dbc.jar"]
