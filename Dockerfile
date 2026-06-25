FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/chat-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", \
  "--spring.datasource.url=${SPRING_DATASOURCE_URL}", \
  "--spring.datasource.username=${SPRING_DATASOURCE_USERNAME}", \
  "--spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}", \
  "--jwt.secret=${JWT_SECRET}"]