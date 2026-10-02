# Backend (Spring Boot) multi-stage build.
# host側にJava/Mavenが無くてもbuildできるよう、builder stageもMaven Wrapper経由でのみbuildする
# (base imageにMavenを同梱しない。Maven本体はWrapperがdistributionType=only-scriptで
#  自身で取得するため、イメージ内にWrapperが指定するバージョンと異なるMavenが混在する余地もない)。

# ---- Builder ----
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY src src

# Windows上でcloneした場合mvnwの改行コードがCRLFになり得るため、実行前に正規化しておく。
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw

RUN ./mvnw package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# pom.xmlのversion変更時もDockerfileの修正が不要なよう、ワイルドカードでjarを1つだけコピーする。
COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
