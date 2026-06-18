# Spring Boot App with Cloud SQL

A production-ready Spring Boot REST API demonstrating clean architecture using **Spring JDBC** (no JPA/Hibernate), **Flyway** database migrations, **Spotless** code formatting, subscription plan management with rebate logic, request/query logging, and connectivity to **Google Cloud SQL PostgreSQL** — designed for deployment on **Cloud Run**.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Project Structure](#3-project-structure)
4. [Architecture and Design Decisions](#4-architecture-and-design-decisions)
5. [JDBC Template — Deep Dive](#5-jdbc-template--deep-dive)
6. [Flyway Database Migrations](#6-flyway-database-migrations)
7. [Rebate Logic](#7-rebate-logic)
8. [Logging Setup](#8-logging-setup)
9. [Spotless Code Formatting](#9-spotless-code-formatting)
10. [Local Development Setup](#10-local-development-setup)
11. [Google Cloud SQL Setup](#11-google-cloud-sql-setup)
12. [Running the Application](#12-running-the-application)
13. [API Reference](#13-api-reference)
14. [Testing the APIs](#14-testing-the-apis)
15. [Verifying Data with Logging](#15-verifying-data-with-logging)
16. [Test Suite](#16-test-suite)
17. [Cloud Run Deployment](#17-cloud-run-deployment)
18. [Coding Standards](#18-coding-standards)
19. [Troubleshooting](#19-troubleshooting)

---

## 1. Project Overview

This project exposes a **User CRUD REST API** backed by PostgreSQL. Each user has a subscription plan and subscription dates. The API includes a rebate calculation endpoint that rewards long-standing and premium subscribers.

Built following strict enterprise coding standards:

- No Lombok — all boilerplate written explicitly
- No JPA/Hibernate — uses Spring JDBC with `NamedParameterJdbcTemplate`
- Constructor injection throughout — no field-level `@Autowired`
- Flyway manages all schema changes — no `ddl-auto=update`
- Spotless enforces Google Java Format on every build
- Full logging — HTTP requests and SQL statements visible in console and GCP Logs Explorer
- Full test coverage across three layers — service (Mockito), DAO (`@JdbcTest`), controller (`@WebMvcTest`)

---

## 2. Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Language |
| Spring Boot | 4.1.0 | Application framework |
| Spring Framework | 7.0.8 | Core framework (shipped with Spring Boot 4) |
| Spring JDBC | 7.0.8 | Database access via `NamedParameterJdbcTemplate` |
| Flyway | bundled via Boot | Database schema version control |
| PostgreSQL | 16 | Relational database |
| Google Cloud SQL | — | Managed PostgreSQL on GCP |
| Cloud SQL Auth Proxy | latest | Secure tunnel for local-to-Cloud SQL connections |
| Spotless | 7.0.4 | Code formatting — Google Java Format |
| SpringDoc OpenAPI | 2.8.9 | Swagger UI |
| JUnit 5 + Mockito | — | Unit and slice testing |
| Gradle | 9.5.1 | Build tool |
| H2 | — | In-memory DB for DAO tests |

---

## 3. Project Structure

```
spring-boot-app-with-cloud-sql/
├── src/
│   ├── main/
│   │   ├── java/com/cloud/sql/spring_boot_app_with_cloud_sql/
│   │   │   ├── SpringBootAppWithCloudSqlApplication.java    # Entry point
│   │   │   ├── config/
│   │   │   │   ├── JacksonConfig.java                       # ObjectMapper — LocalDate as ISO string
│   │   │   │   ├── JdbcConfig.java                         # NamedParameterJdbcTemplate bean
│   │   │   │   └── RequestLoggingConfig.java               # CommonsRequestLoggingFilter bean
│   │   │   ├── controller/
│   │   │   │   └── UserController.java                     # REST endpoints
│   │   │   ├── dto/
│   │   │   │   └── RebateResponse.java                     # Read-only rebate response DTO
│   │   │   ├── entity/
│   │   │   │   ├── SubscriptionType.java                   # Enum: BASIC, PREMIUM
│   │   │   │   └── User.java                               # Plain POJO — no JPA annotations
│   │   │   ├── repo/
│   │   │   │   ├── UserDao.java                            # All SQL lives here
│   │   │   │   └── UserRowMapper.java                      # Maps ResultSet rows → User objects
│   │   │   └── service/
│   │   │       └── UserService.java                        # Business logic and rebate calculation
│   │   └── resources/
│   │       ├── application.properties                      # Datasource, Flyway, logging (git-ignored)
│   │       └── db/migration/
│   │           ├── V1__create_users_table.sql
│   │           └── V2__add_subscription_fields_to_users.sql
│   └── test/
│       └── java/com/cloud/sql/spring_boot_app_with_cloud_sql/
│           ├── controller/UserControllerTest.java
│           ├── repo/UserDaoTest.java
│           └── service/UserServiceTest.java
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
└── .gitignore
```

---

## 4. Architecture and Design Decisions

### Why Spring JDBC instead of JPA/Hibernate?

The coding standards explicitly prohibit adding Hibernate as a direct dependency. Spring JDBC was chosen because:

- **Full SQL control** — you write exactly what hits the database, no generated queries
- **No magic** — no lazy loading, no N+1 query problems, no entity state machine
- **Lighter** — no entity manager, no persistence context, no proxy objects
- **Auditable** — every query is visible in one place (`UserDao`), making code reviews straightforward

### Request flow through layers

```
HTTP Request
     │
     ▼
RequestLoggingFilter  ← logs every incoming request (method, URL, body) before it reaches the controller
     │
     ▼
UserController        ← routing, request parsing, HTTP status codes
     │
     ▼
UserService           ← business logic: rebate calculation, validation, orchestration
     │                  never touches JdbcTemplate directly
     ▼
UserDao               ← all SQL lives here; uses NamedParameterJdbcTemplate
     │
     ▼
UserRowMapper         ← translates one ResultSet row into one User object
     │
     ▼
PostgreSQL / Cloud SQL
```

### Why constructor injection?

Field injection hides dependencies and makes testing without a Spring context impossible. Constructor injection makes dependencies explicit, allows direct instantiation in unit tests, and makes fields `final`.

```java
// Avoid — hidden dependency, untestable without Spring
@Autowired
private UserDao userDao;

// Correct — explicit, testable, immutable
public UserService(UserDao userDao) {
  this.userDao = userDao;
}
```

### Why a DTO for the rebate response?

`RebateResponse` carries computed fields (`rebatePercentage`, `message`) that have no place in the database entity. Keeping computed output separate from the domain model is a core clean-architecture principle.

---

## 5. JDBC Template — Deep Dive

### What is JdbcTemplate?

`JdbcTemplate` is Spring's JDBC abstraction. It eliminates the boilerplate of opening connections, preparing statements, translating checked exceptions, and closing resources.

**Raw JDBC — 20+ lines for one query:**

```java
Connection conn = null;
PreparedStatement ps = null;
ResultSet rs = null;
try {
    conn = dataSource.getConnection();
    ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
    ps.setInt(1, id);
    rs = ps.executeQuery();
    if (rs.next()) {
        return new User(rs.getInt("id"), rs.getString("name"));
    }
} catch (SQLException e) {
    throw new RuntimeException(e);
} finally {
    if (rs != null) rs.close();
    if (ps != null) ps.close();
    if (conn != null) conn.close();
}
```

**With NamedParameterJdbcTemplate — 3 lines:**

```java
jdbcTemplate.query(
    "SELECT id, name FROM users WHERE id = :id",
    Map.of("id", id),
    rowMapper);
```

### JdbcTemplate vs NamedParameterJdbcTemplate

| | `JdbcTemplate` | `NamedParameterJdbcTemplate` |
|---|---|---|
| Parameter style | `?` positional | `:paramName` named |
| Order-sensitive | Yes — wrong order = silent data corruption | No |
| Readability | Low on multi-param queries | High — self-documenting |

This project uses `NamedParameterJdbcTemplate` exclusively.

### JdbcConfig.java

```java
@Configuration
public class JdbcConfig {

  @Bean
  public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }
}
```

Spring Boot auto-configures a `DataSource` from `application.properties`. This bean wraps it in `NamedParameterJdbcTemplate`, making it injectable throughout the application.

### JacksonConfig.java

```java
@Configuration
public class JacksonConfig {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
}
```

Without this, Jackson serializes `LocalDate` as `[2024,1,15]`. With `JavaTimeModule` and timestamps disabled it serializes as `"2024-01-15"`.

> Spring Boot 4 uses Jackson 3.x internally. The property `spring.jackson.serialization.write-dates-as-timestamps=false` fails to bind because the relaxed property binder cannot resolve the enum against the new `tools.jackson` package. Configuring via a `@Bean` bypasses this and works reliably.

### UserRowMapper.java

```java
@Component
public class UserRowMapper implements RowMapper<User> {

  @Override
  public User mapRow(ResultSet rs, int rowNum) throws SQLException {
    LocalDate subscribeDate = toLocalDate(rs.getDate("subscribe_date"));
    LocalDate unsubscribeDate = toLocalDate(rs.getDate("unsubscribe_date"));
    return new User(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getString("plan"),
        subscribeDate,
        unsubscribeDate);
  }

  private LocalDate toLocalDate(Date date) {
    return date != null ? date.toLocalDate() : null;
  }
}
```

`RowMapper<T>` is called once per row. `rs.getDate()` returns `java.sql.Date` — the helper converts it to `java.time.LocalDate`. The null check is essential because SQL `NULL` maps to Java `null`, not a zero date.

### UserDao.java — all SQL in one place

```java
@Repository
public class UserDao {

  private static final String SELECT_ALL =
      "SELECT id, name, plan, subscribe_date, unsubscribe_date FROM users";
}
```

`@Repository` enables Spring's persistence exception translation — raw `SQLException` is automatically converted to a meaningful `DataAccessException` subclass (`DuplicateKeyException`, `BadSqlGrammarException`, etc.).

#### INSERT with generated key

```java
public User insert(User user) {
  String sql =
      "INSERT INTO users (name, plan, subscribe_date, unsubscribe_date)"
          + " VALUES (:name, :plan, :subscribeDate, :unsubscribeDate)";
  KeyHolder keyHolder = new GeneratedKeyHolder();
  jdbcTemplate.update(sql, buildParams(user), keyHolder, new String[]{"id"});
  user.setId(keyHolder.getKey().intValue());
  return user;
}
```

`GeneratedKeyHolder` captures the `SERIAL` primary key assigned by PostgreSQL after the INSERT.

#### SELECT single row — Optional pattern

```java
public Optional<User> findById(Integer id) {
  List<User> results = jdbcTemplate.query(
      SELECT_ALL + " WHERE id = :id", Map.of("id", id), rowMapper);
  return results.stream().findFirst();
}
```

`queryForObject()` is intentionally avoided — it throws `EmptyResultDataAccessException` when no row is found. Using `query()` + `stream().findFirst()` returns a clean `Optional<User>` that the service converts into a meaningful error message.

#### Centralised parameter builder

```java
private MapSqlParameterSource buildParams(User user) {
  return new MapSqlParameterSource()
      .addValue("name", user.getName())
      .addValue("plan", user.getPlan())
      .addValue("subscribeDate", user.getSubscribeDate())
      .addValue("unsubscribeDate", user.getUnsubscribeDate());
}
```

Shared by both `insert()` and `update()`. The update call extends it with `.addValue("id", user.getId())` without duplicating the other fields.

### JdbcTemplate methods reference

| Method | Use case | Returns |
|---|---|---|
| `query(sql, params, rowMapper)` | SELECT multiple rows | `List<T>` |
| `queryForObject(sql, params, rowMapper)` | SELECT exactly one row | `T` (throws if 0 or 2+ rows) |
| `queryForObject(sql, params, Class)` | SELECT a scalar value | scalar (`Integer`, `String`, …) |
| `update(sql, params)` | INSERT / UPDATE / DELETE | `int` rows affected |
| `update(sql, params, keyHolder, cols)` | INSERT capturing generated key | `int`, key in `keyHolder` |
| `batchUpdate(sql, batchParams[])` | Bulk INSERT / UPDATE | `int[]` |

---

## 6. Flyway Database Migrations

Flyway versions schema changes as SQL scripts and applies them in order on startup — like Git for your database.

### How it works

1. Scans `src/main/resources/db/migration/` on every startup
2. Checks `flyway_schema_history` table to see which versions already ran
3. Applies any new scripts in version order, each in its own transaction
4. Aborts startup if a previously applied script has been modified (checksum mismatch)

**Rule**: once a migration has been applied to any real database, never edit it — always create a new version.  
**Exception**: if it has never been applied anywhere yet, you can safely edit it in place.

### Naming convention

```
V{version}__{description}.sql

V1__create_users_table.sql
V2__add_subscription_fields_to_users.sql
V3__add_email_column.sql    ← next migration you add
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

### V2__add_subscription_fields_to_users.sql

```sql
ALTER TABLE users
    ADD COLUMN plan             VARCHAR(50),
    ADD COLUMN subscribe_date   DATE,
    ADD COLUMN unsubscribe_date DATE;
```

### Verify migrations ran

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

---

## 7. Rebate Logic

### Business rules

| Condition | Rebate |
|---|---|
| Subscribed more than 1 year | +10% |
| Plan is PREMIUM | +20% |
| Both conditions met | +30% (stackable, this is the max) |
| Less than 1 year on BASIC, or no subscribe date | 0% |

### Implementation

```java
public RebateResponse calculateRebate(Integer id) {
  User user = findById(id);
  double rebate = 0.0;
  List<String> reasons = new ArrayList<>();

  if (user.getSubscribeDate() != null) {
    long years = ChronoUnit.YEARS.between(user.getSubscribeDate(), LocalDate.now());
    if (years >= 1) {
      rebate += 10.0;
      reasons.add("10% loyalty rebate (subscribed for over 1 year)");
    }
  }

  if (SubscriptionType.PREMIUM.name().equals(user.getPlan())) {
    rebate += 20.0;
    reasons.add("20% Premium plan rebate");
  }

  String message = reasons.isEmpty()
      ? "No rebate applicable for this account."
      : String.join(" + ", reasons) + ". Total: " + (int) rebate + "% off renewal.";

  return new RebateResponse(
      user.getId(), user.getName(), user.getPlan(),
      user.getSubscribeDate(), rebate, message);
}
```

- `ChronoUnit.YEARS.between()` calculates complete elapsed years accurately regardless of leap years
- `SubscriptionType.PREMIUM.name()` keeps the enum as the single source of truth for plan name strings
- `RebateResponse` is immutable (all fields `final`) — it is never persisted

---

## 8. Logging Setup

Logging is configured at two levels: **application logs** (what your Spring Boot app prints) and **Cloud SQL logs** (what PostgreSQL records on the server side).

### application.properties — logging properties

```properties
# Log every incoming HTTP request (method, URL, status code)
logging.level.org.springframework.web=DEBUG

# Log all SQL statements executed by JdbcTemplate
logging.level.org.springframework.jdbc.core=DEBUG

# Log SQL parameter values (actual values bound to each query)
logging.level.org.springframework.jdbc.core.StatementCreatorUtils=TRACE

# Activate the CommonsRequestLoggingFilter bean
logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter=DEBUG
```

### RequestLoggingConfig.java

```java
@Configuration
public class RequestLoggingConfig {

  @Bean
  public CommonsRequestLoggingFilter requestLoggingFilter() {
    CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
    filter.setIncludeQueryString(true);
    filter.setIncludePayload(true);
    filter.setMaxPayloadLength(1000);
    filter.setIncludeHeaders(false);
    filter.setAfterMessagePrefix("REQUEST: ");
    return filter;
  }
}
```

This bean logs every HTTP request body before it reaches the controller. The `logging.level` property in `application.properties` must also be set to `DEBUG` to activate it.

### What you see in the console

When you call `POST /api/users`:

```
DEBUG DispatcherServlet       : POST "/api/users", parameters={}
DEBUG CommonsRequestLoggingFilter : REQUEST: POST /api/users, payload={"name":"Alice","plan":"PREMIUM","subscribeDate":"2023-01-01"}
DEBUG NamedParameterJdbcTemplate : Executing prepared SQL update
DEBUG JdbcTemplate            : Executing prepared SQL statement [INSERT INTO users ...]
TRACE StatementCreatorUtils   : Setting SQL statement parameter value ... value [Alice] ... type [VARCHAR]
TRACE StatementCreatorUtils   : Setting SQL statement parameter value ... value [PREMIUM] ... type [VARCHAR]
DEBUG DispatcherServlet       : Completed 201 CREATED
```

### Enable query logging on Cloud SQL (GCP Console)

By default Cloud SQL only logs errors. To log all queries:

1. GCP Console → **SQL** → click `my-postgres-instance` → **Edit**
2. Scroll to **Flags** → **Add a database flag**
3. Add `log_statement` = `all`
4. Add `log_min_duration_statement` = `0` (logs execution time per query in ms)
5. Click **Save** — the instance restarts briefly

### View logs in GCP Logs Explorer

GCP Console → **Logging** → **Logs Explorer**

**All database activity:**
```
resource.type="cloudsql_database"
resource.labels.database_id="spring-boot-app-with-cloud-sql:my-postgres-instance"
```

**INSERT / UPDATE / DELETE only:**
```
resource.type="cloudsql_database"
resource.labels.database_id="spring-boot-app-with-cloud-sql:my-postgres-instance"
textPayload=~"INSERT|UPDATE|DELETE"
```

**Slow queries (over 500ms):**
```
resource.type="cloudsql_database"
resource.labels.database_id="spring-boot-app-with-cloud-sql:my-postgres-instance"
textPayload=~"duration:"
```

**Connection events (Auth Proxy connect/disconnect):**
```
resource.type="cloudsql_database"
resource.labels.database_id="spring-boot-app-with-cloud-sql:my-postgres-instance"
textPayload=~"connection received|connection authorized|disconnection"
```

A typical GCP log entry for an insert looks like:
```
LOG:  execute <unnamed>: INSERT INTO users (name, plan, subscribe_date, unsubscribe_date)
      VALUES ($1, $2, $3, $4)
DETAIL:  parameters: $1 = 'Alice', $2 = 'PREMIUM', $3 = '2023-01-01', $4 = NULL
```

> For production, set `log_min_duration_statement=500` instead of `log_statement=all`. This logs only queries that take over 500ms, avoiding high log volume.

---

## 9. Spotless Code Formatting

```bash
# Format all Java source files
./gradlew spotlessApply

# Check formatting without modifying files (use in CI)
./gradlew spotlessCheck
```

Configuration in `build.gradle.kts`:

```kotlin
spotless {
  java {
    googleJavaFormat()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
```

Run `spotlessApply` before every commit. The CI build fails on `spotlessCheck` if any file is unformatted.

---

## 10. Local Development Setup

### Prerequisites

- Java 21
- PostgreSQL 16 installed locally
- IntelliJ IDEA or any IDE
- DBeaver (optional — for database inspection)

### Step 1 — Start local PostgreSQL

```powershell
pg_ctl -D "C:\Program Files\PostgreSQL\16\data" start
```

### Step 2 — Create the database (if not already created)

Connect via psql or DBeaver:

```sql
CREATE DATABASE postgres;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE postgres TO postgres;
```

### Step 3 — Create application.properties

This file is git-ignored. Create it at `src/main/resources/application.properties`:

```properties
spring.application.name=spring-boot-app-with-cloud-sql
server.forward-headers-strategy=framework
server.port=8081

spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# Logging
logging.level.org.springframework.web=DEBUG
logging.level.org.springframework.jdbc.core=DEBUG
logging.level.org.springframework.jdbc.core.StatementCreatorUtils=TRACE
logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter=DEBUG
```

### Step 4 — Build and format

```bash
./gradlew spotlessApply build
```

### Step 5 — Run

```bash
./gradlew bootRun
```

Flyway creates the `users` table automatically on first startup.

---

## 11. Google Cloud SQL Setup

### Step 1 — Create a Cloud SQL instance

1. GCP Console → **SQL** → **Create Instance** → **PostgreSQL 16**
2. Instance ID: `my-postgres-instance`
3. Password: strong password for the `postgres` user
4. Region: `us-central1`
5. Availability: Single zone
6. Click **Create Instance** (takes 3–5 minutes)

### Step 2 — Create the application database

Instance overview → **Databases** → **Create Database** → name: `appdb`

### Step 3 — Note your connection name

From the instance overview page, copy the **Connection name**:
```
spring-boot-app-with-cloud-sql:us-central1:my-postgres-instance
```

### Step 4 — Authorize your public IP

```powershell
(Invoke-WebRequest -Uri "https://api.ipify.org" -UseBasicParsing).Content
```

Instance → **Connections** → **Networking** → **Add a Network** → enter `YOUR_IP/32`

> If you get connection refused after some time, your home/office IP may have changed — update this rule.

### Step 5 — Download Cloud SQL Auth Proxy

Download from: https://github.com/GoogleCloudPlatform/cloud-sql-proxy/releases/latest

Download `cloud-sql-proxy.x64.windows.exe`, rename to `cloud-sql-proxy.exe`. This file is in `.gitignore` — do not commit it.

### Step 6 — Authenticate with GCP

```powershell
gcloud auth application-default login
```

### Step 7 — Enable query logging on Cloud SQL

GCP Console → **SQL** → `my-postgres-instance` → **Edit** → **Flags**:

| Flag | Value |
|---|---|
| `log_statement` | `all` |
| `log_min_duration_statement` | `0` |

Click **Save**.

### Step 8 — Switch application.properties to Cloud SQL

```properties
spring.application.name=spring-boot-app-with-cloud-sql
server.forward-headers-strategy=framework
server.port=8081

# Cloud SQL via Auth Proxy
spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/appdb
spring.datasource.username=postgres
spring.datasource.password=YOUR_CLOUD_SQL_PASSWORD

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# Logging
logging.level.org.springframework.web=DEBUG
logging.level.org.springframework.jdbc.core=DEBUG
logging.level.org.springframework.jdbc.core.StatementCreatorUtils=TRACE
logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter=DEBUG

# Cloud Run (uncomment when deploying, comment out the proxy block above)
#spring.datasource.url=jdbc:postgresql:///${DB_NAME:appdb}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME:}&socketFactory=com.google.cloud.sql.postgres.SocketFactory
#spring.datasource.username=${DB_USER:postgres}
#spring.datasource.password=${DB_PASS:}
```

---

## 12. Running the Application

### Terminal 1 — Start the Auth Proxy (keep open)

```powershell
.\cloud-sql-proxy.exe spring-boot-app-with-cloud-sql:us-central1:my-postgres-instance --port=5433
```

Expected:
```
The proxy has started successfully and is ready for new connections!
Listening on 127.0.0.1:5433
```

### Terminal 2 — Start the application

```bash
./gradlew bootRun
```

Expected:
```
o.f.core.internal.command.DbMigrate : Successfully applied 2 migrations to schema "public"
o.s.b.w.e.tomcat.TomcatWebServer    : Tomcat started on port 8081 (http)
```

### Kill a stuck port if needed

```powershell
netstat -ano | findstr :8081
taskkill /PID <PID> /F
```

### Swagger UI

```
http://localhost:8081/swagger-ui/index.html
```

---

## 13. API Reference

Base URL: `http://localhost:8081/api/users`

| Method | Endpoint | Status | Description |
|---|---|---|---|
| POST | `/api/users` | 201 Created | Create a new user |
| GET | `/api/users` | 200 OK | Get all users |
| GET | `/api/users/{id}` | 200 OK | Get user by ID |
| PUT | `/api/users/{id}` | 200 OK | Update a user |
| DELETE | `/api/users/{id}` | 200 OK | Delete a user |
| GET | `/api/users/{id}/rebate` | 200 OK | Calculate renewal rebate |

### User request payload

```json
{
  "name": "Alice",
  "plan": "PREMIUM",
  "subscribeDate": "2022-06-01",
  "unsubscribeDate": null
}
```

`plan` accepts: `BASIC` or `PREMIUM` (or `null`).  
Dates use ISO-8601 format: `YYYY-MM-DD`.

### Rebate response shape

```json
{
  "userId": 1,
  "name": "Alice",
  "plan": "PREMIUM",
  "subscribeDate": "2022-06-01",
  "rebatePercentage": 30.0,
  "message": "10% loyalty rebate (subscribed for over 1 year) + 20% Premium plan rebate. Total: 30% off renewal."
}
```

---

## 14. Testing the APIs

### Terminal 3 — Run curl commands

#### Create a BASIC user (less than 1 year — no rebate)

```powershell
curl -X POST http://localhost:8081/api/users `
  -H "Content-Type: application/json" `
  -d '{\"name\": \"Alice\", \"plan\": \"BASIC\", \"subscribeDate\": \"2025-12-01\"}'
```

#### Create a PREMIUM user subscribed over a year ago (30% rebate)

```powershell
curl -X POST http://localhost:8081/api/users `
  -H "Content-Type: application/json" `
  -d '{\"name\": \"Bob\", \"plan\": \"PREMIUM\", \"subscribeDate\": \"2023-01-01\"}'
```

#### Get all users

```powershell
curl http://localhost:8081/api/users
```

#### Get user by ID

```powershell
curl http://localhost:8081/api/users/1
```

#### Update a user

```powershell
curl -X PUT http://localhost:8081/api/users/1 `
  -H "Content-Type: application/json" `
  -d '{\"name\": \"Alice\", \"plan\": \"PREMIUM\", \"subscribeDate\": \"2022-01-01\"}'
```

#### Delete a user

```powershell
curl -X DELETE http://localhost:8081/api/users/1
```

#### Calculate rebate — Alice (0%)

```powershell
curl http://localhost:8081/api/users/1/rebate
```

#### Calculate rebate — Bob (30%)

```powershell
curl http://localhost:8081/api/users/2/rebate
```

Expected:
```json
{
  "userId": 2,
  "name": "Bob",
  "plan": "PREMIUM",
  "subscribeDate": "2023-01-01",
  "rebatePercentage": 30.0,
  "message": "10% loyalty rebate (subscribed for over 1 year) + 20% Premium plan rebate. Total: 30% off renewal."
}
```

### Rebate scenarios at a glance

| User setup | Expected rebate |
|---|---|
| `plan: BASIC`, subscribed within last year | 0% |
| `plan: BASIC`, subscribed more than 1 year ago | 10% |
| `plan: PREMIUM`, subscribed within last year | 20% |
| `plan: PREMIUM`, subscribed more than 1 year ago | 30% |

---

## 15. Verifying Data with Logging

### What to check in the application console

After every API call, the IntelliJ / terminal window running `bootRun` shows:

```
# The incoming request and its body
DEBUG CommonsRequestLoggingFilter : REQUEST: POST /api/users, payload={"name":"Bob","plan":"PREMIUM","subscribeDate":"2023-01-01"}

# The SQL that ran
DEBUG JdbcTemplate               : Executing prepared SQL statement
      [INSERT INTO users (name, plan, subscribe_date, unsubscribe_date) VALUES (?, ?, ?, ?)]

# The actual parameter values bound to the query
TRACE StatementCreatorUtils      : Setting SQL statement parameter value ... value [Bob]        type [VARCHAR]
TRACE StatementCreatorUtils      : Setting SQL statement parameter value ... value [PREMIUM]    type [VARCHAR]
TRACE StatementCreatorUtils      : Setting SQL statement parameter value ... value [2023-01-01] type [DATE]
TRACE StatementCreatorUtils      : Setting SQL statement parameter value ... value [null]       type [NULL]

# HTTP response status
DEBUG DispatcherServlet          : Completed 201 CREATED
```

This confirms exactly what SQL ran and what values were used — without needing to connect to the database.

### Verify data in DBeaver — Cloud SQL

The Auth Proxy must be running. Open DBeaver and create a connection:

| Field | Value |
|---|---|
| Host | `127.0.0.1` |
| Port | `5433` |
| Database | `appdb` |
| Username | `postgres` |
| Password | your Cloud SQL password |

Run these queries:

```sql
-- See all users
SELECT * FROM users;

-- Confirm both Flyway migrations ran successfully
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

-- Check subscription details with tenure calculation
SELECT id, name, plan, subscribe_date,
       EXTRACT(YEAR FROM AGE(NOW(), subscribe_date)) AS years_subscribed
FROM users
WHERE subscribe_date IS NOT NULL;
```

### Verify data in DBeaver — Local PostgreSQL

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `postgres` |
| Username | `postgres` |
| Password | `postgres` |

Run the same queries. The data will be completely different — local and Cloud SQL are independent databases that never sync. Whichever URL is set in `application.properties` is where data goes.

### Verify in GCP Logs Explorer

GCP Console → **Logging** → **Logs Explorer**

Paste a filter and click **Run Query**:

```
resource.type="cloudsql_database"
resource.labels.database_id="spring-boot-app-with-cloud-sql:my-postgres-instance"
```

You will see every SQL statement the app sent to Cloud SQL with the actual parameter values:

```
LOG:  execute <unnamed>: INSERT INTO users (name, plan, subscribe_date, unsubscribe_date)
      VALUES ($1, $2, $3, $4)
DETAIL:  parameters: $1 = 'Bob', $2 = 'PREMIUM', $3 = '2023-01-01', $4 = NULL
```

To narrow down to writes only:

```
resource.type="cloudsql_database"
resource.labels.database_id="spring-boot-app-with-cloud-sql:my-postgres-instance"
textPayload=~"INSERT|UPDATE|DELETE"
```

### Summary — three ways to verify

| Method | What it shows | When to use |
|---|---|---|
| Application console (TRACE logs) | Exact SQL + values the app sent | During development — instant feedback |
| DBeaver | Actual rows in the database | Confirm data was persisted correctly |
| GCP Logs Explorer | Server-side SQL log from PostgreSQL | Confirm Cloud SQL received the query |

---

## 16. Test Suite

### Run all tests

```bash
./gradlew test
```

### Run with formatting first (recommended before committing)

```bash
./gradlew spotlessApply test
```

### Results

```
24 tests — 24 passed, 0 failed, 0 skipped
```

### UserServiceTest — pure unit test

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock private UserDao userDao;
  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userDao); // constructor injection makes this one line
  }
}
```

No Spring context loaded. Runs in milliseconds. Covers: create, findAll, findById, findById-not-found, update, delete, and all five rebate scenarios (0%, 10%, 20%, 30%, no-subscribe-date).

### UserDaoTest — JDBC slice test

```java
@JdbcTest
@Import({UserDao.class, UserRowMapper.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false"})
@Sql(statements = { "CREATE TABLE IF NOT EXISTS users (...)" })
class UserDaoTest { }
```

- `@JdbcTest` starts only the JDBC slice — no web layer, no services — wired with H2
- Flyway is disabled so it does not try to apply PostgreSQL-specific SQL to H2
- Schema is created via `@Sql` before each test
- Covers: insert, insert with subscription fields, findAll, findById, findById-empty, update, deleteById

### UserControllerTest — web layer slice test

```java
@WebMvcTest(UserController.class)
class UserControllerTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private UserService userService;
}
```

- `@WebMvcTest` loads only the web layer — no service or DAO wiring, no Tomcat started
- `MockMvc` sends requests in-process and inspects the response
- Covers: POST 201, GET 200, GET by ID 200, PUT 200, DELETE 200, rebate response shape

---

## 17. Cloud Run Deployment

### Step 1 — Switch datasource to Socket Factory

In `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql:///${DB_NAME:appdb}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME:}&socketFactory=com.google.cloud.sql.postgres.SocketFactory
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASS:}
```

### Step 2 — Build and push Docker image

```bash
gcloud builds submit --tag gcr.io/spring-boot-app-with-cloud-sql/spring-boot-app
```

### Step 3 — Deploy

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

Flyway runs on startup and applies pending migrations to Cloud SQL automatically.

---

## 18. Coding Standards

| Rule | Detail |
|---|---|
| No Lombok | Getters, setters, constructors written explicitly |
| No direct Hibernate | Spring JDBC used; JPA not on the classpath |
| Constructor injection | Never `@Autowired` on fields |
| `@ResponseStatus` on every handler | POST → `201 Created`, all others → `200 OK` |
| `@RestController` | All controller classes |
| `@Service` | All service classes |
| `@Repository` | All DAO classes — enables exception translation |
| `@Configuration` | All config classes in the `config` package |
| Named parameters only | `NamedParameterJdbcTemplate` — never positional `?` |
| Flyway constraint naming | `TABLE_PK`, `TABLE_FK`, `TABLE_COL_IDX` |
| Spotless on every build | `./gradlew spotlessApply` before commit |
| DTOs for output shapes | Computed fields do not belong in the entity |

---

## 19. Troubleshooting

### App starts but returns "relation users does not exist"

Flyway did not run. Check:
- `spring.flyway.enabled=true` is in `application.properties`
- The datasource URL points to the correct database (`appdb` for Cloud SQL, `postgres` for local)
- Migration files are under `src/main/resources/db/migration/` with correct `V{n}__` prefix

### Port already in use

```powershell
netstat -ano | findstr :8081
taskkill /PID <PID> /F
```

### Cloud SQL connection refused

- Confirm the Auth Proxy is still running in its terminal
- Confirm `spring.datasource.url` uses `127.0.0.1:5433`
- Confirm your current public IP is still authorized: Instance → Connections → Networking
- Re-authenticate if credentials expired: `gcloud auth application-default login`

### Flyway checksum mismatch on startup

You edited a migration file that was already applied. Options:
- Local dev database only: drop the database and let Flyway re-apply from scratch
- Shared environment with real data: create a new `V{n+1}__` migration for the change

### LocalDate serializes as `[2024, 1, 15]` instead of `"2024-01-15"`

`JacksonConfig` is not being picked up. Verify it exists in the `config` package, is annotated `@Configuration`, and the `@Bean` method registers `JavaTimeModule` with `WRITE_DATES_AS_TIMESTAMPS` disabled.

### No SQL logs appearing in the console

Verify these three lines are in `application.properties`:
```properties
logging.level.org.springframework.jdbc.core=DEBUG
logging.level.org.springframework.jdbc.core.StatementCreatorUtils=TRACE
logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter=DEBUG
```

### Tests fail with `IllegalStateException: Failed to load ApplicationContext`

Run `./gradlew test --info` and look for the innermost `Caused by`. Common causes:
- Property binding failure — check `application.properties` for unsupported keys
- Missing `@Import` on `@JdbcTest` — `UserDao` and `UserRowMapper` must be imported explicitly
- Missing `@MockitoBean` on `@WebMvcTest` — all service dependencies of the controller must be mocked
