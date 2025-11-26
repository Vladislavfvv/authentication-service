# Быстрый старт - Пошаговая инструкция

Все команды для каждого проекта выполняются в соответствующей папке проекта


## Шаг 1: Очистка (если нужно)

# Остановка и удаление всех контейнеров и volumes
docker compose down -v


## Шаг 2: Создание сети (если не существует)

!!!**Важно**: Сеть `backend-network` определена как `external: true` в `docker-compose.yml`, 
поэтому её нужно создать вручную перед запуском сервисов!!!

# Проверка существования сети
docker network ls | Select-String "backend-network"

# Создание сети (если не существует)
docker network create backend-network
  external: true  # ← Сеть должна существовать заранее!!!


## Шаг 3: Запуск authentication-service
docker compose up -d --build


**Ожидание**: Подождите 1-2 минуты, пока все контейнеры станут `healthy`.

Проверка статуса:
docker compose ps


## Шаг 4: Проверка создания админа в authentication-service


### 4.1. Проверка в auth_db (через Liquibase)

docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role, first_name, last_name FROM public.users WHERE login = 'admin@tut.by';"

**Ожидаемый результат**: Админ с ID=1, login=admin@tut.by, role=ROLE_ADMIN


### 4.2. Проверка в Keycloak

Откройте браузер: http://localhost:8090

1. Войдите в **master realm**:
   - Username: `admin`
   - Password: `admin` (из `env/keycloak.env`)


2. Переключитесь на realm **authentication-service**


3. Перейдите в **Users** → найдите `admin@tut.by`


4. Проверьте:
   - ✅ **Email Verified**: `ON` (галочка установлена)
   - ✅ **Required Actions**: пусто (нет требований)
   - ✅ **Realm Roles**: `ROLE_ADMIN`
   - ✅ **Enabled**: `ON`
В **authentication-service** проверьте  также:
Перейдите: Clients → authentication-service-client → Service account roles
Нажмите "Assign role" → Filter by clients → realm-management
Выберите: manage-users, view-users, manage-realm

или это же можно запустить через скрипт python:
Установка зависимостей (если нужно)
Если библиотека requests не установлена:
pip install requests
Или через python.exe:
python.exe -m pip install requests
Запуск скрипта
Перейдите в директорию проекта, запустите скрипт:
python setup-service-account-roles.py
Или:
python.exe setup-service-account-roles.py


Важно!!! перезапустите  authentication-service — он должен получить новый токен с правами


## Шаг 5: Запуск user-service

cd E:\Innowise\Java\UserService\userService
docker compose up -d --build

**Ожидание**: Подождите 30-60 секунд, пока контейнеры станут `healthy`.

Проверка статуса:
docker compose ps


## Шаг 6: Проверка создания админа в user-service

docker exec us_db_pg psql -U postgres -d us_db -c "SELECT id, name, surname, email FROM userservice_data.users WHERE email = 'admin@tut.by';"


**Ожидаемый результат**: Админ с ID=1, email=admin@tut.by

## Шаг 7: Проверка работоспособности

### 7.1. Health checks

# authentication-service
http://localhost:8081/actuator/health

# user-service
http://localhost:8082/actuator/health


### 7.2. Логин админа
POST 
http://localhost:8081/auth/login 
"Content-Type: application/json" 
'{\"login\":\"admin@tut.by\",\"password\":\"admin\"}'

**Ожидаемый результат**: JSON с `accessToken` и `refreshToken`


### 7.3. Доступ к user-service

# Замените YOUR_TOKEN на accessToken из предыдущего запроса
$TOKEN = "YOUR_TOKEN"
GET http://localhost:8082/api/v1/users/me -H "Authorization: Bearer $TOKEN"


## Итоговая проверка

Все три базы данных должны содержать админа с ID=1:

# auth_db
docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role FROM public.users WHERE login = 'admin@tut.by';"

# us_db_pg
docker exec us_db_pg psql -U postgres -d us_db -c "SELECT id, email FROM userservice_data.users WHERE email = 'admin@tut.by';"

# Keycloak (через веб-интерфейс)
# http://localhost:8090 → authentication-service realm → Users → admin@tut.by


## ### Roadmap работы программы **ROLE_USER**:

    1)Регистрируем пользователя по URL:
    POST http://localhost:8081/auth/register
    Content-Type: application/json
    прописываем например:
    Body:  raw, JSON
{
        "login": "MyLogin@example.com",
        "password": "password",
        "firstName": "someName",
        "lastName": "someLastName",
  "role": "ROLE_USER"
}

    2) Логируемся:
    POST http://localhost:8081/auth/login
    Content-Type: application/json
    Body:  raw, JSON
    {
      "login": "MyLogin@example.com",
      "password": "password" 
    }

    3) Получаем токен:
    POST http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
    Content-Type: application/json
    Body x-www-form-urlencoded
    client_id       authentication-service-client
    client_secret   n3mJkSY8O1HDhLZGz4sOmHnmgd6SAM5t  это берем в настройках Кейклок.
    username        MyLogin@example.com
    password        password
    grant_type      password

    Ответ:
      json
      {
        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "type": "Bearer",
        "expiresIn": 900000
      }
      

    4) Копируем полученный токен из поля access_token:
    Например: Ваш_токен

    5) Вставляем его в следующий запрос(это уже запрос полетит на user-service) - по нему удостоверимся что такой пользователь есть в базе и возьмем его id из базы:
    GET http://localhost:8082/api/v1/users/me
    Auth-> Auth Type: Bearer Token
    Например: Ваш_токен

    6) Берем из ответа полученный id:
    Например полученный ответ: 
    {
      "id": 26,   <- вот он наш id
      "name": "someName",
      "surname": "someLastName",
      "birthDate": null,
      "email": "MyLogin@example.com",
      "cards": []
    }

    7) Далее варианты(не забываем в каждом запросе постоянно вставлять Auth-> Auth Type: Bearer Token Например: Ваш_токен!!!):

      --  посмотреть информацию о пользователе:
        GET http://localhost:8082/api/v1/users/me

      --  дополнить недостающую информацию о пользователе:
        PUT http://localhost:8082/api/v1/users/26
        Например
        { 
          "birthDate": "2002-01-01", <- дополнить, т.к. это поле пустое
          "email": "MyLogin@example.com",
          "cards": [  <- дополнить, т.к. карт нет
            {
              "userId": 26,
              "number": "2533567812341375",
              "holder": "someName someLastName",
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

    1) Авторизоваться через `POST http://localhost:8081/auth/login`:
    Content-Type: application/json
    {
      "login": "admin@tut.by",
      "password": "admin"
    }

    2) Получаем токен:
    POST http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
    Content-Type: application/json
    Body x-www-form-urlencoded
    client_id       authentication-service-client
    client_secret   n3mJkSY8O1HDhLZGz4sOmHnmgd6SAM5t  это берем в настройках Кейклок.
    username        admin@example.com
    password        admin
    grant_type      password

    3) Копируем полученный токен из поля access_token:
    Например: Ваш_токен  

    4) Далее варианты(не забываем в каждом запросе постоянно вставлять Auth-> Auth Type: Bearer Token Например: Ваш_токен!!!):
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

    - удаление пользователя:
    DELETE http://localhost:8082/api/v1/users/28


