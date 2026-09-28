#Stage 1
#Maven + JDK 17 image (works on Intel and Apple Silicon; the alpine images are Intel-only)
FROM maven:3.9-eclipse-temurin-17 AS build

#set working directory
WORKDIR /app

COPY pom.xml .

#get all dependencies but don't build yet
RUN mvn -B dependency:go-offline

#copy source code
COPY src ./src

#build application, skip tests for now (MIGHT NEED TO CHANGE THIS LATER)
RUN mvn -B clean package -DskipTests

#Stage 2
FROM eclipse-temurin:17-jre

WORKDIR /app

#Copy from first build
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
