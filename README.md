# Task Management Service

Сервис управления задачами, реализованный в рамках тестового задания.

## Описание

Приложение позволяет:

- создавать задачи;
- получать список задач с пагинацией;
- получать задачу по id;
- назначать исполнителя задаче;
- менять статус задачи;
- отправлять события в Kafka при создании задачи и назначении исполнителя.

Авторизация и регистрация пользователей не реализованы, так как они вынесены за рамки тестового задания.

## Технологии

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Kafka
- PostgreSQL
- Apache Kafka
- Docker
- Docker Compose
- Maven

## Модель данных

### Task

Задача содержит:

- `id`
- `title`
- `description`
- `status`
- `assignee`

### User

Пользователь содержит:

- `id`
- `name`
- `email`

## Статусы задачи

Доступные значения:

```text
NEW
IN_PROGRESS
DONE
CANCELLED
```

## Kafka

Приложение отправляет события в Kafka topic:

```text
task-events
```

Отправляемые события:

```text
TASK_CREATED
TASK_ASSIGNED
```

`TASK_CREATED` отправляется при создании задачи.

`TASK_ASSIGNED` отправляется при назначении исполнителя задаче.

## Запуск проекта

Для запуска необходимы:

- Docker
- Docker Compose

Собрать и запустить проект:

```bash
docker compose up --build
```

После запуска приложение будет доступно по адресу:

```text
http://localhost:8080
```

PostgreSQL доступен на внешнем порту:

```text
5433
```

Kafka доступна на внешнем порту:

```text
9092
```

## REST API

### Создать задачу

```http
POST /tasks
```

Пример запроса:

```json
{
  "title": "Test task",
  "description": "Check REST API"
}
```

Пример ответа:

```json
{
  "id": 1,
  "title": "Test task",
  "description": "Check REST API",
  "status": "NEW",
  "assigneeId": null,
  "assigneeName": null
}
```

---

### Получить задачу по id

```http
GET /tasks/{id}
```

Пример запроса:

```http
GET /tasks/1
```

Пример ответа:

```json
{
  "id": 1,
  "title": "Test task",
  "description": "Check REST API",
  "status": "NEW",
  "assigneeId": null,
  "assigneeName": null
}
```

---

### Получить задачи с пагинацией

```http
GET /tasks?page=0&size=10
```

Пример запроса:

```http
GET /tasks?page=0&size=10
```

Пример ответа содержит поле `content` со списком задач и служебную информацию о странице.

---

### Назначить исполнителя задаче

```http
PATCH /tasks/{taskId}/assignee/{userId}
```

Пример запроса:

```http
PATCH /tasks/1/assignee/1
```

Пример ответа:

```json
{
  "id": 1,
  "title": "Test task",
  "description": "Check REST API",
  "status": "NEW",
  "assigneeId": 1,
  "assigneeName": "Igor"
}
```

---

### Изменить статус задачи

```http
PATCH /tasks/{taskId}/status
```

Пример запроса:

```http
PATCH /tasks/1/status
```

Тело запроса:

```json
{
  "status": "IN_PROGRESS"
}
```

Пример ответа:

```json
{
  "id": 1,
  "title": "Test task",
  "description": "Check REST API",
  "status": "IN_PROGRESS",
  "assigneeId": 1,
  "assigneeName": "Igor"
}
```

## Тестовые пользователи

При запуске приложения автоматически создаются тестовые пользователи:

```text
Igor
Anna
Bob
```

Их можно использовать для назначения исполнителя задаче.

Пример:

```http
PATCH /tasks/1/assignee/1
```

## Проверка Kafka-сообщений

Зайти в контейнер Kafka:

```bash
docker exec -it task-management-kafka bash
```

Прочитать сообщения из topic:

```bash
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic task-events --from-beginning
```

После создания задачи или назначения исполнителя в консоли должны появиться события.

Пример события при создании задачи:

```json
{
  "eventType": "TASK_CREATED",
  "taskId": 1,
  "title": "Test task",
  "assigneeId": null
}
```

Пример события при назначении исполнителя:

```json
{
  "eventType": "TASK_ASSIGNED",
  "taskId": 1,
  "title": "Test task",
  "assigneeId": 1
}
```

## Остановка проекта

Остановить контейнеры:

```bash
docker compose down
```

Остановить контейнеры и удалить данные PostgreSQL:

```bash
docker compose down -v
```

## Примечания по запуску

Приложение запускается в Docker вместе с PostgreSQL и Kafka.

Внутри Docker Compose приложение подключается к сервисам по внутренним адресам:

```text
postgres:5432
kafka:29092
```

При локальном запуске из IDE используются внешние адреса:

```text
127.0.0.1:5433
localhost:9092
```
---

## Автоматические тесты

Проект содержит тесты нескольких уровней:

- **Unit-тесты (модульные тесты)** `TaskServiceTest` — проверяют бизнес-логику сервиса с Mockito без запуска Spring-контекста;
- **Unit-тесты Kafka producer (производителя Kafka)** `TaskEventProducerTest` — проверяют topic, key и содержимое событий `TASK_CREATED` и `TASK_ASSIGNED`;
- **MVC-тесты** `TaskControllerTest` — проверяют REST API, валидацию запросов, пагинацию и обработку ошибок через MockMvc;
- **Integration-тесты (интеграционные тесты)** `TaskFlowIntegrationTest` — поднимают настоящие PostgreSQL и Kafka через Testcontainers и проверяют полную цепочку `HTTP → Service → PostgreSQL → Kafka`.

### Проверяемые сценарии

- создание задачи со статусом `NEW`;
- публикация события `TASK_CREATED`;
- получение задачи по id;
- получение страницы задач;
- назначение исполнителя;
- публикация события `TASK_ASSIGNED`;
- изменение статуса задачи;
- сохранение данных в PostgreSQL;
- чтение опубликованных событий настоящим Kafka consumer (потребителем Kafka);
- ответы `400 Bad Request` при ошибках валидации;
- ответы `404 Not Found` для отсутствующей задачи или пользователя.

### Запуск отдельных наборов тестов

Только unit- и MVC-тесты, без Docker:

```bash
mvn -Dtest=TaskServiceTest,TaskEventProducerTest,TaskControllerTest test
```

Только интеграционные тесты:

```bash
mvn -Dtest=TaskFlowIntegrationTest test
```

Перед запуском интеграционных тестов должен быть запущен Docker Desktop. Самостоятельно запускать `docker compose` не нужно: Testcontainers создаёт изолированные контейнеры PostgreSQL и Kafka на время тестов.

Полная проверка проекта и формирование отчёта JaCoCo:

```bash
mvn clean verify
```

HTML-отчёт о покрытии после выполнения `verify`:

```text
target/site/jacoco/index.html
```

## Обработка ошибок

API возвращает структурированный JSON-ответ. Пример для отсутствующей задачи:

```json
{
  "timestamp": "2026-06-17T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Task not found with id: 999",
  "path": "/tasks/999",
  "validationErrors": {}
}
```

При запуске с профилем `test` класс `DataInitializer` отключается, поэтому интеграционные тесты полностью управляют своими тестовыми данными.
