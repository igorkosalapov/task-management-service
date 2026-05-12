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