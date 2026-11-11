# Etapa 1: compila o .jar com Maven (essa imagem só existe durante o build)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN MAVEN_OPTS=-Xmx256m mvn -q package

# Etapa 2: imagem final, só com o Java necessário para executar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/ramdex-api.jar .

RUN mkdir -p /logs
VOLUME /logs

ENV JAVA_OPTS="-Xmx512m -XX:+UseG1GC -Xlog:gc*:file=/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar ramdex-api.jar >> /logs/application.log 2>&1"]
