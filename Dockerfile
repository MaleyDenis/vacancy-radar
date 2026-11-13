FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# dependencies in their own layer: a code change does not download them again
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre-alpine
# fixed uid: the host data dir is chowned to it
RUN addgroup -S -g 10001 radar && adduser -S -u 10001 -G radar radar
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER radar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
