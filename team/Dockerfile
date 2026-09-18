#Stage 1
#use lightweight image for building
FROM eclipse-temurin:17-jdk-alpine as build

RUN apk add --no-cache maven

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
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

#Copy from first build
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
