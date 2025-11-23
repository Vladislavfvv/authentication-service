# Быстрый старт - Пошаговая инструкция

Все команды для каждого проекта выполняются в соответствующей папке проекта
Для начала просмотреть запущенные контейнеры:
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

## Шаг 1: Очистка предыдущих данных 
одна команда на 3 действия(остановка + удаление контейнеров + удаление volumes и сетей)
cd E:\Innowise\Java\UserService\userService; docker compose down -v --remove-orphans

Или по другому: 
1.1 Удаление образов:
docker rmi -f userservice-user-service:latest
docker rmi -f authentication-service-authentication-service:latest

Команды для проверки и работы с контейнером user-service (для authentication-service аналогично)
docker ps -a | grep user-service        # контейнер
docker images | grep user-service       # образ
docker volume ls | grep user-service    # volume
docker network ls | grep user-service   # сети
docker logs user-service                # логи
docker inspect user-service             # проверить, какие volumes подключены к контейнеру - искать секцию "Mounts"
docker exec -it user-service shилиbash  # зайти внутрь


## Шаг 2: Создание сети (если не существует)

!!!**Важно**: Сеть `backend-network` определена как `external: true` в `docker-compose.yml`, 
поэтому её нужно создать вручную перед запуском сервисов!!!

# Проверка существования сети
docker network ls | Select-String "backend-network"
# Проверить, в какой сети работает контейнер
docker inspect user-service --format='{{json .NetworkSettings.Networks}}' | jq .
Проверить, какие volumes подключены к контейнеру
docker inspect user-service --format='{{json .Mounts}}' | jq .

# 2.1 Создание сети (если не существует)
docker network create backend-network
  external: true  # ← Сеть должна существовать заранее!!!


## Шаг 3. Пересобрать проект через Maven (создаст новый JAR)
Для authentication-service: перейти в папку с проектом
mvn clean package -DskipTests


## Шаг 4. Пересобрать и запустить Docker контейнеры
docker-compose up --build -d

**Ожидание**: Подождите 1-2 минуты, пока все контейнеры станут `healthy`.

Проверка статуса:
docker compose ps


## Шаг 5: Проверка создания админа в authentication-service
через Docker в командной строке проекта:

docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role, first_name, last_name FROM public.users WHERE login = 'admin@tut.by';"

**Ожидаемый результат**: Админ с ID=1, login=admin@tut.by, role=ROLE_ADMIN


## Шаг 6: Запуск user-service
Для user-service: перейти в папку с проектом
Пересобрать проект через Maven (создаст новый JAR)
mvn clean package -DskipTests

## Шаг 7. Пересобрать и запустить Docker контейнеры
docker compose up -d --build

**Ожидание**: Подождите 30-60 секунд, пока контейнеры станут `healthy`.

Проверка статуса:
docker compose ps


## Шаг 8: Проверка создания админа в user-service

docker exec us_db psql -U postgres -d us_db -c "SELECT id, name, surname, email FROM public.users WHERE email = 'admin@tut.by';"


**Ожидаемый результат**: Админ с ID=1, email=admin@tut.by

## Шаг 8: Проверка работоспособности

### 8.1. Health checks

# authentication-service
http://localhost:8081/actuator/health

# user-service
http://localhost:8082/actuator/health


### 8.2. Логин админа
POST 
http://localhost:8081/auth/login 
"Content-Type: application/json" 
'{\"login\":\"admin@tut.by\",\"password\":\"admin\"}'

**Ожидаемый результат**: JSON с `accessToken` и `refreshToken`


## Итоговая проверка

Обе базы данных должны содержать админа с ID=1:

# auth_db
docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role FROM public.users WHERE login = 'admin@tut.by';"

# us_db
docker exec us_db psql -U postgres -d us_db -c "SELECT id, email FROM public.users WHERE email = 'admin@tut.by';"


## ### Roadmap работы программы **ROLE_USER**:

    1)Регистрируем пользователя по URL:
    POST http://localhost:8081/auth/v1/register
    Content-Type: application/json
    прописываем например:
    Body:  raw, JSON
      {
        "login": "MyLogin@example.com",
        "password": "password",       
        "role": "ROLE_USER"
      }
    Ответ: User registered successfully. Please login to get tokens.

    2) Логируемся:
    POST http://localhost:8081/auth/v1/login
    Content-Type: application/json
    Body:  raw, JSON
    {
      "login": "MyLogin@example.com",
      "password": "password" 
    }
    Получаем ответ:
      json
      {
        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "type": "Bearer",
        "expiresIn": 900000
      }      

    4) Копируем полученный токен из поля access_token:
    Например: Ваш_токен

    5) Вставляем его в следующий запрос(это уже запрос полетит на user-service) - по нему добавляем пользователя в БД, а также получаем его id из базы:
    POST http://localhost:8082/api/v1/users/createUser
    Auth-> Auth Type: Bearer Token  
    Например: Ваш_токен
    { 
    "name": "newUserName",
    "surname": "newUserSurname",
    "birthDate": "1990-10-01"
    }


    6) Берем из ответа полученный id:
    Например полученный ответ: 
    {
      "id": 26,   <- вот он наш id
      "name": "newUserName",
      "surname": "newUserSurname",
      "birthDate": 1990-10-01,
      "email": "MyLogin@example.com",
      "cards": []
    }

    7) Далее варианты(не забываем в каждом запросе постоянно вставлять Auth-> Auth Type: Bearer Token Например: Ваш_токен!!!):

      --  посмотреть информацию о пользователе:
        GET http://localhost:8082/api/v1/users/self

      --  дополнить недостающую информацию о пользователе:
        PUT http://localhost:8082/api/v1/users/26
        Например
        {           
          "cards": [  
            {
              "userId": 26, <- это можно не указывать, т.к. Id берется следующий за последним в БД
              "number": "2533567812341375",
              "holder": "someName someLastName", это можно не указывать, т.к. вставляется name + surname
              "expirationDate": "2026-12-31"
            },
            {
              "userId": 26,
              "number": "3533432187654472",
              "holder": "someName someLastName",
              "expirationDate": "2027-06-30"
            }
          ]
        }


      --  посмотреть информацию о картах пользователя:
        GET http://localhost:8082/api/v1/cards
        Выведет результат: в виде json карты пользователя



### Roadmap работы программы **ROLE_ADMIN**:

    1) Авторизоваться через `POST http://localhost:8081/auth/v1/login`:
    Content-Type: application/json
    {
      "login": "admin@tut.by",
      "password": "admin"
    }   
    **Ожидаемый результат**: JSON с `accessToken` и `refreshToken`

    2) Копируем полученный токен из поля access_token:
    Например: Ваш_токен  

    3) Далее варианты(не забываем в каждом запросе постоянно вставлять Auth-> Auth Type: Bearer Token Например: Ваш_токен!!!):
    ROLE_ADMIN предоставлен широкий спектр возможностей:

    - посмотреть всех юзеров пагинация:
    GET http://localhost:8082/api/v1/users?page=4&size=5

    - посмотреть все карты пагинация:
    GET http://localhost:8082/api/v1/cards?page=4&size=10

    - редактирование пользователя
    PUT http://localhost:8082/api/v1/users/30
    Например:
    raw: x-www-form-urlencoded
    { 
      "birthDate": "2002-01-01",
      "email": "newuser15@example.com",
      "cards": [
        {
          "userId": 30,
          "number": "2933555855349975",
          "holder": "newUser10 newUser10",
          "expirationDate": "2026-12-31"
        },
        {
          "userId": 30,
          "number": "1933465667699472",
          "holder": "newUser10 newUser10",
          "expirationDate": "2027-06-30"
        }
      ]
    }

    4) Удаление пользователя:
    DELETE http://localhost:8082/api/v1/users/28


