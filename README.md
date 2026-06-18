# Spring Boot App with Cloud SQL

A production-ready Spring Boot REST API that demonstrates clean architecture using **Spring JDBC** (no JPA/Hibernate), **Flyway** database migrations, **Spotless** code formatting, and connectivity to **Google Cloud SQL PostgreSQL** — deployable on **Cloud Run**.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Project Structure](#3-project-structure)
4. [Architecture & Design Decisions](#4-architecture--design-decisions)
5. [JDBC Template — Deep Dive](#5-jdbc-template--deep-dive)
6. [Flyway Database Migrations](#6-flyway-database-migrations)
7. [Spotless Code Formatting](#7-spotless-code-formatting)
8. [Local Development Setup](#8-local-development-setup)
9. [Google Cloud SQL Setup](#9-google-cloud-sql-setup)
10. [Running the Application](#10-running-the-application)
11. [API Reference](#11-api-reference)
12. [Testing](#12-testing)
13. [Verifying Data](#13-verifying-data)
14. [Cloud Run Deployment](#14-cloud-run-deployment)
15. [Coding Standards](#15-coding-standards)

---

## 1. Project Overview

This project exposes a simple **User CRUD REST API** backed by PostgreSQL. It was built following strict enterprise coding standards:

- No Lombok — all boilerplate written explicitly
- No direct Hibernate/JPA dependency — uses Spring JDBC with `NamedParameterJdbcTemplate`
- Constructor injection throughout — no field-level `@Autowired`
- Flyway manages all schema changes — no `ddl-auto=update`
- Spotless enforces Google Java Format on every build
- Full unit test coverage — service (Mockito), DAO (`@JdbcTest`), controller (`@WebMvcTest`)

---

## 2. Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Language |
| Spring Boot | 4.1.0 | Application framework |
| Spring JDBC | 7.0.8 | Database access via JdbcTemplate |
| Flyway | 12.4.0 | Database schema migrations |
| PostgreSQL | 16 | Database |
| Google Cloud SQL | - | Managed PostgreSQL on GCP |
| Spotless | 7.0.4 | Code formatting (Google Java Format) |
| SpringDoc OpenAPI | 2.8.9 | Swagger UI |
| JUnit 5 + Mockito | - | Unit testing |
| Gradle | 9.5.1 | Build tool |

---

## 3. Project Structure

```
spring-boot-app-with-cloud-sql/
├── src/
│   ├── main/
│   │   ├── java/com/cloud/sql/spring_boot_app_with_cloud_sql/
│   │   │   ├── SpringBootAppWithCloudSqlApplication.java   # Entry point
│   │   │   ├── config/
│   │   │   │   └── JdbcConfig.java                         # NamedParameterJdbcTemplate bean
│   │   │   ├── controller/
│   │   │   │   └── UserController.java                     # REST endpoints
│   │   │   ├── entity/
│   │   │   │   └── User.java                               # Plain POJO (no JPA annotations)
│   │   │   ├── repo/
│   │   │   │   ├── UserDao.java                            # Data access using JdbcTemplate
│   │   │   │   └── UserRowMapper.java                      # Maps ResultSet rows to User objects
│   │   │   └── service/
│   │   │       └── UserService.java                        # Business logic
│   │   └── resources/
│   │       ├── application.properties                      # App configuration (git-ignored)
│   │       └── db/migration/
│   │           └── V1__create_users_table.sql              # Flyway migration
│   └── test/
│       └── java/com/cloud/sql/spring_boot_app_with_cloud_sql/
│           ├── controller/UserControllerTest.java          # @WebMvcTest
│           ├── repo/UserDaoTest.java                       # @JdbcTest
│           └── service/UserServiceTest.java                # Pure Mockito
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
└── .gitignore
```

---

## 4. Architecture & Design Decisions

### Why Spring JDBC instead of JPA/Hibernate?

The coding standards explicitly prohibit adding Hibernate or Lombok as direct dependencies. Spring JDBC was chosen because:

- **Full SQL control** — you write exactly what hits the database, no surprises
- **No magic** — no lazy loading issues, no N+1 query problems, no entity state management
- **Lighter** — no entity manager, no persistence context, no proxy-wrapped entities
- **Explicit** — every query is visible in the DAO class, making the codebase easier to audit

### Layer responsibilities

```
HTTP Request
     │
     ▼
UserController      ← handles HTTP, maps request/response, sets status codes
     │
     ▼
UserService         ← business logic, never touches JdbcTemplate directly
     │
     ▼
UserDao             ← all SQL lives here, uses NamedParameterJdbcTemplate
     │
     ▼
UserRowMapper       ← converts a ResultSet row into a User object
     │
     ▼
PostgreSQL
```

Each layer has one job. The service never touches the database directly. The DAO never contains business logic.

---

## 5. JDBC Template — Deep Dive

### What is JdbcTemplate?

`JdbcTemplate` is Spring's core JDBC abstraction. It eliminates the boilerplate of opening connections, creating statements, handling exceptions, and closing resources — all of which you would have to do manually with raw JDBC.

**Raw JDBC (without Spring) — 20+ lines for a simple query:**
```java
Connection conn = dataSource.getConnection();
PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
ps.setInt(1, id);
ResultSet rs = ps.executeQuery();
User user = null;
if (rs.next()) {
    user = new User(rs.getInt("id"), rs.getString("name"));
}
rs.close();
ps.close();
conn.close(); // must be in finally block or it leaks
```

**With NamedParameterJdbcTemplate — 3 lines:**
```java
jdbcTemplate.query(
    "SELECT id, name FROM users WHERE id = :id",
    Map.of("id", id),
    rowMapper);
```

Spring handles connection acquisition, statement preparation, exception translation, and resource cleanup automatically.

### JdbcTemplate vs NamedParameterJdbcTemplate

| | `JdbcTemplate` | `NamedParameterJdbcTemplate` |
|---|---|---|
| Parameter style | `?` positional | `:name` named |
| Readability | Lower (order matters) | Higher (self-documenting) |
| Error-prone | Yes (wrong order = wrong data) | No |
| Example | `WHERE id = ?` | `WHERE id = :id` |

This project uses `NamedParameterJdbcTemplate` exclusively because named parameters are safer and easier to read — especially in complex queries with many parameters.

### JdbcConfig.java — Registering the Bean

```java
@Configuration
public class JdbcConfig {

  @Bean
  public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }
}
```

Spring Boot auto-configures a `DataSource` from `application.properties`. This config class takes that `DataSource` and wraps it in a `NamedParameterJdbcTemplate`, making it available for injection anywhere in the application. The `@Bean` method uses constructor-style parameter injection — the `DataSource` is injected by Spring automatically.

### UserRowMapper.java — Mapping ResultSet to Object

```java
@Component
public class UserRowMapper implements RowMapper<User> {

  @Override
  public User mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new User(rs.getInt("id"), rs.getString("name"));
  }
}
```

A `RowMapper<T>` is a functional interface with one method: `mapRow`. It is called once per row returned by a query. Spring passes you the `ResultSet` already positioned at the current row — you just read columns by name and construct your object.

- `rs.getInt("id")` — reads the `id` column as an integer
- `rs.getString("name")` — reads the `name` column as a String
- `rowNum` — the current row number, useful if you need row-position-specific logic

The `@Component` annotation registers it as a Spring bean so it can be injected into `UserDao`.

### UserDao.java — All SQL in One Place

```java
@Repository
public class UserDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final UserRowMapper rowMapper;

  public UserDao(NamedParameterJdbcTemplate jdbcTemplate, UserRowMapper rowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
  }
}
```

`@Repository` serves two purposes:
1. Marks this class as a Spring-managed DAO component
2. Enables Spring's persistence exception translation — any database exception is automatically wrapped in a meaningful Spring `DataAccessException` (e.g., `BadSqlGrammarException`, `DuplicateKeyException`) instead of a raw `SQLException`

#### INSERT with generated key retrieval

```java
public User insert(User user) {
  String sql = "INSERT INTO users (name) VALUES (:name)";
  MapSqlParameterSource params = new MapSqlParameterSource()
      .addValue("name", user.getName());
  KeyHolder keyHolder = new GeneratedKeyHolder();
  jdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});
  user.setId(keyHolder.getKey().intValue());
  return user;
}
```

- `MapSqlParameterSource` — a map of named parameters to their values
- `GeneratedKeyHolder` — captures the database-generated primary key after insert
- `new String[]{"id"}` — tells the JDBC driver which column holds the generated key
- After `update()`, `keyHolder.getKey()` contains the new `id` assigned by the PostgreSQL `SERIAL` sequence

#### SELECT all rows

```java
public List<User> findAll() {
  return jdbcTemplate.query("SELECT id, name FROM users", rowMapper);
}
```

`query()` executes the SQL, iterates every row, calls `rowMapper.mapRow()` for each, and returns a `List<User>`. If no rows exist, it returns an empty list (never null).

#### SELECT single row by ID

```java
public Optional<User> findById(Integer id) {
  List<User> results = jdbcTemplate.query(
      "SELECT id, name FROM users WHERE id = :id",
      Map.of("id", id),
      rowMapper);
  return results.stream().findFirst();
}
```

`query()` is used instead of `queryForObject()` deliberately. `queryForObject()` throws `EmptyResultDataAccessException` when no row is found — requiring a try/catch. Using `query()` and `stream().findFirst()` returns an `Optional<User>` cleanly, which the service layer converts to a meaningful exception.

#### UPDATE

```java
public User update(User user) {
  String sql = "UPDATE users SET name = :name WHERE id = :id";
  jdbcTemplate.update(sql, Map.of("name", user.getName(), "id", user.getId()));
  return user;
}
```

`Map.of()` is a concise shorthand for passing named parameters inline. The `update()` method returns the number of affected rows — available if you need to validate that a row was actually found and updated.

#### DELETE

```java
public void deleteById(Integer id) {
  jdbcTemplate.update("DELETE FROM users WHERE id = :id", Map.of("id", id));
}
```

The same `update()` method handles INSERT, UPDATE, and DELETE — anything that modifies data uses this method.

### Key JdbcTemplate methods summary

| Method | Use case | Returns |
|---|---|---|
| `query(sql, params, rowMapper)` | SELECT multiple rows | `List<T>` |
| `queryForObject(sql, params, rowMapper)` | SELECT exactly one row | `T` (throws if 0 or 2+ rows) |
| `queryForObject(sql, params, Class)` | SELECT a scalar value | scalar (e.g., `Integer`, `String`) |
| `update(sql, params)` | INSERT / UPDATE / DELETE | `int` (rows affected) |
| `update(sql, params, keyHolder, cols)` | INSERT with auto-generated key | `int`, key in `keyHolder` |
| `batchUpdate(sql, batchParams)` | Bulk INSERT / UPDATE | `int[]` |

---

## 6. Flyway Database Migrations

Flyway is a database migration tool that versions your schema changes as SQL scripts and applies them in order — exactly like Git for your database.

### How it works

1. On application startup, Flyway scans `src/main/resources/db/migration/`
2. It checks a `flyway_schema_history` table in the database to see which migrations have already run
3. It applies any new migrations in version order
4. If a previously applied migration file is modified, Flyway fails the startup — protecting against accidental schema drift

### Naming convention

```
V{version}__{description}.sql

V1__create_users_table.sql
V2__add_email_to_users.sql
V3__create_orders_table.sql
```

### V1__create_users_table.sql

```sql
CREATE TABLE IF NOT EXISTS users
(
    id   SERIAL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT users_pk PRIMARY KEY (id)
);
```

- `SERIAL` — PostgreSQL auto-increment (assigns the next value from a sequence on every insert)
- `CONSTRAINT users_pk PRIMARY KEY (id)` — named constraint following the `TABLE_PK` naming standard from the coding guidelines
- `IF NOT EXISTS` — safe to run even if the table already exists

### Verifying migrations ran

```sql
SELECT * FROM flyway_schema_history;
```

---

## 7. Spotless Code Formatting

Spotless enforces **Google Java Format** automatically.

```bash
# Format all Java files
./gradlew spotlessApply

# Check formatting without changing files (useful in CI)
./gradlew spotlessCheck
```

Google Java Format enforces 2-space indentation, import ordering, line length limits, and blank line rules. Run `spotlessApply` before every commit.

---

## 8. Local Development Setup

### Prerequisites

- Java 21
- PostgreSQL installed locally
- IntelliJ IDEA

### Step 1 — Create local database

```sql
CREATE DATABASE postgres;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE postgres TO postgres;
```

### Step 2 — Create application.properties

This file is git-ignored. Create it at `src/main/resources/application.properties`:

```properties
spring.application.name=spring-boot-app-with-cloud-sql
server.forward-headers-strategy=framework

spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

### Step 3 — Run

```bash
./gradlew bootRun
```

Flyway creates the `users` table automatically on first startup.

---

## 9. Google Cloud SQL Setup

### Step 1 — Create Cloud SQL Instance

1. GCP Console → **SQL** → **Create Instance** → **PostgreSQL 16**
2. Instance ID: `my-postgres-instance`
3. Password: strong password for `postgres` user
4. Region: `us-central1`
5. Zonal availability: Single zone (sufficient for development)

### Step 2 — Create Database

Instance → **Databases** → **Create Database** → name it `appdb`

### Step 3 — Note your connection name

From the instance overview page, copy the **Connection name**:
```
spring-boot-app-with-cloud-sql:us-central1:my-postgres-instance
```

### Step 4 — Authorize your public IP

```powershell
# Find your public IP
(Invoke-WebRequest -Uri "https://api.ipify.org" -UseBasicParsing).Content
```

Instance → **Connections** → **Networking** → **Add a Network** → enter `YOUR_IP/32`

### Step 5 — Download Cloud SQL Auth Proxy

Download from: `https://github.com/GoogleCloudPlatform/cloud-sql-proxy/releases/latest`

Download `cloud-sql-proxy.x64.windows.exe`, rename to `cloud-sql-proxy.exe`. Do not commit this file (it is in `.gitignore`).

### Step 6 — Authenticate with GCP

```powershell
gcloud auth application-default login
```

### Step 7 — Run Auth Proxy (keep this terminal open)

```powershell
.\cloud-sql-proxy.exe spring-boot-app-with-cloud-sql:us-central1:my-postgres-instance --port=5433
```

Expected output:
```
The proxy has started successfully and is ready for new connections!
```

### Step 8 — Switch application.properties to Cloud SQL

```properties
spring.application.name=spring-boot-app-with-cloud-sql
server.forward-headers-strategy=framework

# Cloud SQL via Auth Proxy (local dev)
spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/appdb
spring.datasource.username=postgres
spring.datasource.password=YOUR_CLOUD_SQL_PASSWORD

# Cloud Run (uncomment when deploying)
#spring.datasource.url=jdbc:postgresql:///${DB_NAME:appdb}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME:}&socketFactory=com.google.cloud.sql.postgres.SocketFactory
#spring.datasource.username=${DB_USER:postgres}
#spring.datasource.password=${DB_PASS:}

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

---

## 10. Running the Application

### Kill any process on port 8080/8081 if needed

```powershell
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Run from IntelliJ terminal

```bash
./gradlew bootRun
```

### Successful startup output

```
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "public"
o.s.boot.tomcat.TomcatWebServer     : Tomcat started on port 8081 (http)
SpringBootAppWithCloudSqlApplication : Started in 2.5 seconds
```

### Swagger UI

```
http://localhost:8081/swagger-ui/index.html
```

---

## 11. API Reference

Base URL: `http://localhost:8081/api/users`

| Method | Endpoint | Status | Description |
|---|---|---|---|
| POST | `/api/users` | 201 Created | Create a new user |
| GET | `/api/users` | 200 OK | Get all users |
| GET | `/api/users/{id}` | 200 OK | Get user by ID |
| PUT | `/api/users/{id}` | 200 OK | Update user name |
| DELETE | `/api/users/{id}` | 200 OK | Delete user |

### curl examples (PowerShell)

```powershell
# Create
curl -X POST http://localhost:8081/api/users -H "Content-Type: application/json" -d '{\"name\": \"Alice\"}'

# Get all
curl http://localhost:8081/api/users

# Get by ID
curl http://localhost:8081/api/users/1

# Update
curl -X PUT http://localhost:8081/api/users/1 -H "Content-Type: application/json" -d '{\"name\": \"Alice Updated\"}'

# Delete
curl -X DELETE http://localhost:8081/api/users/1
```

---

## 12. Testing

### Run all tests

```bash
./gradlew test
```

### Test layers

#### UserServiceTest — Pure unit test

Uses Mockito to mock `UserDao`. Tests business logic in isolation with no Spring context and no database.

```
covers: create, findAll, findById, findById (not found), update, delete
```

#### UserDaoTest — JDBC slice test

Uses `@JdbcTest` with an in-memory H2 database. Tests real SQL execution without needing PostgreSQL running. Flyway is disabled and the schema is created via `@Sql`.

```
covers: insert, findAll, findById, findById (empty), update, deleteById
```

#### UserControllerTest — Web layer slice test

Uses `@WebMvcTest` — starts only the web layer. `UserService` is mocked with `@MockitoBean`. Tests HTTP status codes, JSON serialization, and routing.

```
covers: POST 201, GET 200, GET by ID 200, PUT 200, DELETE 200
```

---

## 13. Verifying Data

### DBeaver — Local PostgreSQL

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `postgres` |
| Username | `postgres` |
| Password | `postgres` |

### DBeaver — Cloud SQL (proxy must be running)

| Field | Value |
|---|---|
| Host | `127.0.0.1` |
| Port | `5433` |
| Database | `appdb` |
| Username | `postgres` |
| Password | your Cloud SQL password |

### SQL verification queries

```sql
-- See all users
SELECT * FROM users;

-- Check Flyway ran successfully
SELECT * FROM flyway_schema_history;

-- Count users
SELECT COUNT(*) FROM users;
```

### Important: Local and Cloud SQL are independent

Local PostgreSQL and Cloud SQL are completely separate databases. Data goes only to whichever `spring.datasource.url` is active in `application.properties`. They do not sync.

### View logs in GCP

GCP Console → **Logging** → **Logs Explorer**:

```
resource.type="cloudsql_database"
resource.labels.database_id="spring-boot-app-with-cloud-sql:my-postgres-instance"
```

---

## 14. Cloud Run Deployment

### Step 1 — Switch to Cloud SQL Socket Factory in application.properties

```properties
spring.datasource.url=jdbc:postgresql:///${DB_NAME:appdb}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME:}&socketFactory=com.google.cloud.sql.postgres.SocketFactory
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASS:}
```

### Step 2 — Uncomment socket factory in build.gradle.kts

```kotlin
implementation("com.google.cloud.sql:postgres-socket-factory:1.21.0")
```

### Step 3 — Build and push Docker image

```bash
gcloud builds submit --tag gcr.io/spring-boot-app-with-cloud-sql/spring-boot-app
```

### Step 4 — Deploy to Cloud Run

```bash
gcloud run deploy spring-boot-app \
  --image gcr.io/spring-boot-app-with-cloud-sql/spring-boot-app \
  --platform managed \
  --region us-central1 \
  --add-cloudsql-instances spring-boot-app-with-cloud-sql:us-central1:my-postgres-instance \
  --set-env-vars INSTANCE_CONNECTION_NAME=spring-boot-app-with-cloud-sql:us-central1:my-postgres-instance \
  --set-env-vars DB_NAME=appdb \
  --set-env-vars DB_USER=postgres \
  --set-env-vars DB_PASS=YOUR_PASSWORD
```

---

## 15. Coding Standards

This project follows O'Reilly enterprise coding standards:

| Rule | Detail |
|---|---|
| No Lombok | Getters, setters, constructors written explicitly |
| No direct Hibernate | Spring JDBC used instead of JPA |
| Constructor injection | Never field-level `@Autowired` |
| `@ResponseStatus` | POST returns `201 Created`, all others `200 OK` |
| `@RestController` | All controller classes |
| `@Service` | All service classes |
| `@Repository` | All DAO classes — enables exception translation |
| Config in `config` package | Annotated with `@Configuration` |
| Named parameters | `NamedParameterJdbcTemplate` over positional `?` |
| Flyway constraint naming | `TABLE_PK`, `TABLE_FK`, `TABLE_COL_IDX` |
| Spotless | Google Java Format enforced on every build |
| `implementation` scope | Never deprecated `compile` |
