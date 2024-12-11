   # Используйте официальный образ Maven как базу
   FROM maven:3.8.4-openjdk-17 AS build

   # Установите рабочую директорию
   WORKDIR /app

   # Скопируйте pom.xml и зависимости
   COPY pom.xml .
   COPY src ./src

   # Сборка проекта
   RUN mvn clean package

   # Используйте минимальный образ для запуска
   FROM openjdk:17-jdk-slim

   # Установите рабочую директорию
   WORKDIR /app

   # Скопируйте собранный jar файл из предыдущего этапа сборки
   COPY --from=build /app/target/TelegramBot-1.0-SNAPSHOT.jar app.jar

   # Запустите приложение
   ENTRYPOINT ["java", "-jar", "app.jar"]