# Этап сборки
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Установка Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Копирование pom.xml для кэширования зависимостей
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Копирование исходного кода
COPY src ./src

# Сборка проекта
RUN mvn clean package -DskipTests

# Поиск и копирование JAR файла (исключаем original и sources JAR)
RUN JAR_FILE=$(find /app/target -name "*.jar" ! -name "*-sources.jar" ! -name "*-original.jar" -type f | head -1) && \
    if [ -n "$JAR_FILE" ]; then cp "$JAR_FILE" /app/app.jar; else echo "JAR file not found!" && ls -la /app/target/ && exit 1; fi

# Этап выполнения
FROM eclipse-temurin:21-jdk
VOLUME /tmp
WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]