# =============================================================================
# Almoxarifado - build multi-stage.
#
# Estagio 1 (jdk 26): compila com o Gradle wrapper versionado no repo.
# Estagio 2 (jre 26): so o runtime + o jar. Nada de JDK, Gradle ou fontes na
#                     imagem final.
# =============================================================================

FROM eclipse-temurin:26-jdk AS build

WORKDIR /src

# Wrapper e scripts de build primeiro: enquanto build.gradle nao mudar, o
# download do Gradle e das dependencias fica em cache entre builds.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle

# O repo veio do Windows: gradlew chega sem bit de execucao e possivelmente com
# CRLF, que quebra o shebang. Normaliza os dois.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Baixa a distribuicao do Gradle numa camada propria.
RUN ./gradlew --no-daemon --version

COPY src src

# -x test: a suite roda em H2 (perfil test) e nao valida o alvo real; rodar no
# build so atrasaria a imagem sem aumentar a confianca no deploy.
RUN ./gradlew --no-daemon clean bootJar -x test


# =============================================================================
FROM eclipse-temurin:26-jre

# curl para o HEALTHCHECK abaixo. --no-install-recommends mantem a imagem magra.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/*

# O servidor roda em UTC, mas a aplicacao grava LocalDateTime (horario civil,
# sem conversao de fuso). Sem esta linha toda movimentacao ficaria 3h adiantada.
ENV TZ=America/Sao_Paulo

# Usuario sem privilegios: se a app for comprometida, o atacante nao e root
# dentro do container.
RUN useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar

# Unico diretorio de escrita da aplicacao (UPLOADS_DIR). Recebe volume no compose.
RUN mkdir -p /data/uploads && chown -R 10001:10001 /data /app

USER 10001

EXPOSE 8080

# MaxRAMPercentage: sem isso a JVM dimensiona o heap pela memoria do host
# inteiro, ignorando o limite do container.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

# start-period generoso: o primeiro boot roda as migrations do Flyway.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
