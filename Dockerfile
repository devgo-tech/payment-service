#INSTALLATION OF THE OPERATING SYSTEM
FROM eclipse-temurin:17-jdk
COPY target/payment-service-dev-1.jar payment-service.jar
EXPOSE 8096
ENTRYPOINT ["java","-Dspring.profiles.active=prod","-jar","payment-service.jar"]
