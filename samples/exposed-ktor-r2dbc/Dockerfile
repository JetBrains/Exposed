FROM openjdk:17
EXPOSE 8080:8080
RUN mkdir /app
COPY ./build/libs/*-all.jar /app/exposed-ktor-r2dbc.jar
ENTRYPOINT ["java","-jar","/app/exposed-ktor-r2dbc.jar"]