# Sử dụng JDK 17 làm nền tảng
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Sau này khi bạn build code Java, file .jar sẽ được copy vào đây
# Hiện tại chúng ta để sẵn cấu hình này
COPY iot-web-app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
