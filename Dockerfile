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
EXPOSE 8080
# A saída da aplicação (stdout/stderr) vai para /logs/application.log.
# Monte um diretório do host em /logs (docker run --mount type=bind,src=...,dst=/logs).
# Flags da JVM (heap, log do GC) entram pela variável JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar ramdex-api.jar >> /logs/application.log 2>&1"]
