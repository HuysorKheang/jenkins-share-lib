FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B -DskipTests package
EXPOSE 8080
CMD ["java","-jar","target/*.jar"]
