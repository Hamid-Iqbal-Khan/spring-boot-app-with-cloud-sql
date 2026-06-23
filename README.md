# Spring Boot REST API with Cloud SQL (PostgreSQL) + Cloud Run

A production-ready Spring Boot 4.1.0 REST API that persists data to Cloud SQL (PostgreSQL) on Google Cloud Run. Features Flyway migrations, rebate calculation logic, Swagger UI, JaCoCo coverage, and SonarCloud integration.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Domain: Rebate Logic](#domain-rebate-logic)
- [API Endpoints](#api-endpoints)
- [Database Migrations](#database-migrations)
- [Running Locally](#running-locally)
- [Running Tests](#running-tests)
- [Code Quality (Spotless + SonarCloud)](#code-quality-spotless--sonarcloud)
- [GCP Setup: Dashboard + CLI](#gcp-setup-dashboard--cli)
- [Deploy to Cloud Run](#deploy-to-cloud-run)
- [Verify the Deployment](#verify-the-deployment)
- [Redeploy](#redeploy)

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 4.1.0 (Spring Framework 7.0.8) |
| Language | Java 21 |
| Database | PostgreSQL (Cloud SQL) |
| Data Access | Spring JDBC — `NamedParameterJdbcTemplate` |
| Migrations | Flyway |
| Build | Gradle (Groovy DSL) |
| Container | Docker (multi-stage, Eclipse Temurin 21) |
| CI/CD | Google Cloud Build |
| Runtime | Google Cloud Run |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Coverage | JaCoCo |
| Static Analysis | SonarCloud (org.sonarqube 6.2.0.5505) |
| Formatting | Spotless (Google Java Format) |

**No Lombok. No JPA/Hibernate.** Plain POJOs, manual getters/setters, constructor injection.

---

## Project Structure

```
src/
├── main/java/com/cloud/sql/spring_boot_app_with_cloud_sql/
│   ├── config/
│   │   ├── JacksonConfig.java          # ObjectMapper bean — LocalDate ISO-8601
│   │   ├── JdbcConfig.java             # NamedParameterJdbcTemplate bean
│   │   └── RequestLoggingConfig.java   # CommonsRequestLoggingFilter (HTTP body logging)
│   ├── controller/UserController.java  # CRUD + /rebate endpoint
│   ├── dto/RebateResponse.java         # Immutable response DTO
│   ├── entity/
│   │   ├── User.java                   # id, name, plan, subscribe_date, unsubscribe_date
│   │   └── SubscriptionType.java       # Enum: BASIC, PREMIUM
│   ├── repo/
│   │   ├── UserDao.java                # SQL queries via NamedParameterJdbcTemplate
│   │   └── UserRowMapper.java          # ResultSet -> User
│   └── service/UserService.java        # Business logic + calculateRebate()
└── main/resources/
    ├── application.properties          # Local profile (gitignored — contains credentials)
    ├── application.properties.template # Safe committed template (no credentials)
    ├── application-cloudrun.properties # Cloud Run profile (Socket Factory)
    └── db/migration/
        ├── V1__create_users_table.sql
        ├── V2__add_subscription_fields_to_users.sql
        └── V3__seed_users.sql

src/test/java/
├── controller/UserControllerTest.java  # 6 tests (@WebMvcTest)
├── repo/UserRowMapperTest.java         # 2 tests (mocked ResultSet — LocalDate mapping)
└── service/UserServiceTest.java        # 11 tests (Mockito)
```

---

## Domain: Rebate Logic

| Condition | Rebate |
|-----------|--------|
| PREMIUM plan + subscribed > 1 year | 30% |
| PREMIUM plan + subscribed <= 1 year | 20% |
| BASIC plan + subscribed > 1 year | 10% |
| BASIC plan + subscribed <= 1 year or no date | 0% |
| Unsubscribed (unsubscribe_date is set) | 0% |

Rebates are **stackable up to 30%** (loyalty 10% + premium 20%).

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users` | List all users |
| GET | `/api/users/{id}` | Get user by ID |
| POST | `/api/users` | Create user |
| PUT | `/api/users/{id}` | Update user |
| DELETE | `/api/users/{id}` | Delete user |
| GET | `/api/users/{id}/rebate` | Calculate rebate for user |
| GET | `/actuator/health` | Health check |
| GET | `/swagger-ui/index.html` | Swagger UI |

**Live Cloud Run URL:** `https://spring-boot-app-rire725v4q-uc.a.run.app`

Try it now:
- Health: `https://spring-boot-app-rire725v4q-uc.a.run.app/actuator/health`
- Users: `https://spring-boot-app-rire725v4q-uc.a.run.app/api/users`
- Swagger: `https://spring-boot-app-rire725v4q-uc.a.run.app/swagger-ui/index.html`

---

## Database Migrations

Flyway runs automatically on startup. Migrations are in `src/main/resources/db/migration/`.

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__create_users_table.sql` | Create `users` table with `id SERIAL` |
| V2 | `V2__add_subscription_fields_to_users.sql` | Add `plan`, `subscribe_date`, `unsubscribe_date` |
| V3 | `V3__seed_users.sql` | 10 seed users covering all rebate scenarios |

### V3 Seed Users

| User | Plan | Subscribe Date | Scenario |
|------|------|----------------|----------|
| Alice Johnson | PREMIUM | 2022-03-15 | 30% rebate |
| Bob Smith | PREMIUM | 2021-11-01 | 30% rebate |
| Carol White | BASIC | 2023-01-20 | 10% rebate |
| David Brown | BASIC | 2022-08-05 | 10% rebate |
| Emma Davis | PREMIUM | 2025-12-01 | 20% rebate |
| Frank Miller | PREMIUM | 2026-02-14 | 20% rebate |
| Grace Wilson | BASIC | 2026-04-10 | 0% rebate |
| Henry Moore | BASIC | 2026-01-30 | 0% rebate |
| Isabella Taylor | BASIC | NULL | 0% rebate |
| James Anderson | PREMIUM | 2021-06-01 (unsubscribed 2024-12-31) | 0% rebate |

Flyway is idempotent — it tracks applied migrations in `flyway_schema_history` and never re-runs them. Running the app multiple times will not duplicate seed data.

---

## Running Locally

### Prerequisites

- Java 21
- PostgreSQL running locally (or Cloud SQL Auth Proxy)
- `cloud-sql-proxy.exe` (gitignored — do not commit)

### 1. Copy the properties template

```bash
cp src/main/resources/application.properties.template src/main/resources/application.properties
```

Edit `application.properties` and fill in your local DB credentials. **This file is gitignored and must never be committed.**

### 2. Start Cloud SQL Auth Proxy (if connecting to Cloud SQL locally)

```powershell
.\cloud-sql-proxy.exe spring-boot-app-with-cloud-sql:us-central1:my-postgres-instance --port=5433
```

The proxy binary is gitignored. Download it from the [cloud-sql-proxy releases](https://github.com/GoogleCloudPlatform/cloud-sql-proxy/releases) page.

### 3. Run the app

```bash
./gradlew bootRun
```

App starts on `http://localhost:8080`.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health: `http://localhost:8080/actuator/health`

### 4. Verify data locally (DBeaver)

Connect DBeaver to Cloud SQL via the proxy:

| Setting | Value |
|---------|-------|
| Host | `localhost` |
| Port | `5433` |
| Database | `appdb` |
| Username | `postgres` |
| Password | (from Secret Manager) |

Verify queries:

```sql
-- All users
SELECT * FROM users ORDER BY id;

-- Flyway migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Count per plan
SELECT plan, COUNT(*) FROM users GROUP BY plan;

-- Unsubscribed users
SELECT * FROM users WHERE unsubscribe_date IS NOT NULL;

-- Users subscribed > 1 year
SELECT name, plan, subscribe_date,
       DATE_PART('year', AGE(subscribe_date)) AS years_subscribed
FROM users
WHERE subscribe_date IS NOT NULL
  AND unsubscribe_date IS NULL
ORDER BY subscribe_date;
```

---

## Running Tests

```bash
./gradlew test
```

**19 tests total** — 6 controller (`@WebMvcTest`) + 11 service (Mockito) + 2 repo (`UserRowMapperTest` with mocked `ResultSet`). H2 in-memory database has been fully removed; no DAO integration tests exist.

Coverage report after test run: `build/reports/jacoco/test/html/index.html`

---

## Code Quality (Spotless + SonarCloud)

### Spotless (auto-formatting)

```bash
# Check
./gradlew spotlessCheck

# Fix
./gradlew spotlessApply
```

### SonarCloud (static analysis + coverage)

**Organization:** `spring-boot-app-with-cloud-sq`
**Project key:** `spring-boot-app-with-cloud-sql`
**Dashboard:** `https://sonarcloud.io/project/overview?id=spring-boot-app-with-cloud-sql`

**Current quality metrics:**

| Metric | Status |
|--------|--------|
| Open Issues | 0 |
| Coverage (overall) | ~60% |
| Duplications | 0.0% |
| Security Rating | A |
| Reliability Rating | A |
| Maintainability Rating | A |

**Exclusions** (not analysed): `**/config/**`, `**/dto/**`, `**/entity/**`

**Dependency locking** is enabled — `gradle.lockfile` pins all transitive dependency versions, satisfying SonarCloud's reproducible-build security requirement.

To run a fresh analysis:

1. Set your SonarCloud token (never put this in code):

```powershell
$env:SONAR_TOKEN = "your_token_here"
```

2. Run the full pipeline (clean → test → coverage → sonar):

```bash
./gradlew clean test jacocoTestReport sonar
```

To generate/update the dependency lockfile (run when dependencies change):

```bash
./gradlew dependencies --write-locks
```

---

## GCP Setup: Dashboard + CLI

**Project:** `spring-boot-app-with-cloud-sql`
**Project Number:** `32568250152`
**Region:** `us-central1`

### Step 1 — Enable APIs

**Dashboard:** GCP Console -> APIs & Services -> Enable APIs -> enable each service below.

**CLI:**
```bash
gcloud config set project spring-boot-app-with-cloud-sql

gcloud services enable \
  cloudbuild.googleapis.com \
  run.googleapis.com \
  sqladmin.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  cloudresourcemanager.googleapis.com
```

### Step 2 — Create Artifact Registry

**Dashboard:** Artifact Registry -> Create Repository -> name: `spring-boot-repo`, format: Docker, region: `us-central1`.

**CLI:**
```bash
gcloud artifacts repositories create spring-boot-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="Spring Boot Docker images"
```

### Step 3 — Create Service Account

**Dashboard:** IAM & Admin -> Service Accounts -> Create Service Account -> name: `spring-boot-sa`.

**CLI:**
```bash
gcloud iam service-accounts create spring-boot-sa \
  --display-name="Spring Boot App SA" \
  --project=spring-boot-app-with-cloud-sql
```

SA email: `spring-boot-sa@spring-boot-app-with-cloud-sql.iam.gserviceaccount.com`

Grant required roles:

```bash
SA="spring-boot-sa@spring-boot-app-with-cloud-sql.iam.gserviceaccount.com"
PROJECT="spring-boot-app-with-cloud-sql"

gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$SA" --role="roles/cloudsql.client"
gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$SA" --role="roles/run.admin"
gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$SA" --role="roles/artifactregistry.writer"
gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$SA" --role="roles/secretmanager.secretAccessor"
gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$SA" --role="roles/iam.serviceAccountUser"
```

Grant the Cloud Build SA permission to deploy:

```bash
CLOUD_BUILD_SA="32568250152@cloudbuild.gserviceaccount.com"

gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$CLOUD_BUILD_SA" --role="roles/run.admin"
gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$CLOUD_BUILD_SA" --role="roles/iam.serviceAccountUser"
gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$CLOUD_BUILD_SA" --role="roles/artifactregistry.writer"
```

### Step 4 — Create Cloud SQL Instance

**Dashboard:** SQL -> Create Instance -> PostgreSQL -> instance ID: `my-postgres-instance`, region: `us-central1`, set postgres password.

**CLI:**
```bash
gcloud sql instances create my-postgres-instance \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region=us-central1 \
  --project=spring-boot-app-with-cloud-sql
```

Create the database and set the password:

```bash
gcloud sql databases create appdb --instance=my-postgres-instance

gcloud sql users set-password postgres \
  --instance=my-postgres-instance \
  --password=YOUR_PASSWORD
```

### Step 5 — Store DB Password in Secret Manager

**Dashboard:** Security -> Secret Manager -> Create Secret -> name: `db-password`, paste your postgres password as the secret value.

**CLI:**
```bash
echo -n "YOUR_PASSWORD" | gcloud secrets create db-password \
  --data-file=- \
  --project=spring-boot-app-with-cloud-sql
```

---

## Deploy to Cloud Run

### First deploy

```powershell
gcloud builds submit . --config=cloudbuild.yaml --substitutions='_SA_EMAIL=spring-boot-sa@spring-boot-app-with-cloud-sql.iam.gserviceaccount.com,_TAG=v1.0.0'
```

> **PowerShell note:** Wrap the entire `--substitutions` value in single quotes so PowerShell does not split on commas.

### Grant public access (run once after first deploy)

The `--allow-unauthenticated` flag in `cloudbuild.yaml` sets the service property, but the IAM policy binding must also be added separately:

```bash
gcloud run services add-iam-policy-binding spring-boot-app \
  --region=us-central1 \
  --member="allUsers" \
  --role="roles/run.invoker"
```

### Monitor build progress

```bash
# List recent builds
gcloud builds list --limit=5

# Stream logs for a specific build
gcloud builds log BUILD_ID
```

---

## Verify the Deployment

### API checks (curl)

```bash
# Health check
curl https://spring-boot-app-rire725v4q-uc.a.run.app/actuator/health

# List all users (returns 10 seed users from V3 migration)
curl https://spring-boot-app-rire725v4q-uc.a.run.app/api/users

# Create a new user
curl -X POST https://spring-boot-app-rire725v4q-uc.a.run.app/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","plan":"PREMIUM","subscribeDate":"2023-01-01"}'

# Rebate for user 1 (Alice Johnson — PREMIUM, subscribed 2022 = 30%)
curl https://spring-boot-app-rire725v4q-uc.a.run.app/api/users/1/rebate
```

### Cloud SQL via DBeaver (through Cloud SQL Auth Proxy)

Start the proxy locally:
```powershell
.\cloud-sql-proxy.exe spring-boot-app-with-cloud-sql:us-central1:my-postgres-instance --port=5433
```

Connect DBeaver: `localhost:5433` / database `appdb` / user `postgres`.

Verify queries:

```sql
-- All 10 seed users
SELECT id, name, plan, subscribe_date, unsubscribe_date FROM users ORDER BY id;

-- Confirm all 3 Flyway migrations ran successfully
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

-- Expected rebate per user (calculated in SQL)
SELECT
    name,
    plan,
    subscribe_date,
    unsubscribe_date,
    CASE
        WHEN unsubscribe_date IS NOT NULL                                         THEN '0%  (unsubscribed)'
        WHEN subscribe_date IS NULL                                               THEN '0%  (no date)'
        WHEN plan = 'PREMIUM' AND subscribe_date <= CURRENT_DATE - INTERVAL '1 year' THEN '30% (premium + loyal)'
        WHEN plan = 'PREMIUM'                                                     THEN '20% (premium)'
        WHEN plan = 'BASIC'   AND subscribe_date <= CURRENT_DATE - INTERVAL '1 year' THEN '10% (loyal)'
        ELSE                                                                           '0%  (basic, new)'
    END AS expected_rebate
FROM users
ORDER BY id;
```

### Cloud Logging (Cloud Run request logs)

**Dashboard:** Cloud Run -> `spring-boot-app` -> Logs tab.

**CLI:**
```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="spring-boot-app"' \
  --limit=50 \
  --format="table(timestamp,textPayload)"
```

HTTP request bodies appear in logs because `RequestLoggingConfig` registers `CommonsRequestLoggingFilter`.

---

## Redeploy

Increment the tag for each new deployment:

```powershell
gcloud builds submit . --config=cloudbuild.yaml --substitutions='_SA_EMAIL=spring-boot-sa@spring-boot-app-with-cloud-sql.iam.gserviceaccount.com,_TAG=v1.0.1'
```

The `_TAG` substitution tags the Docker image in Artifact Registry and deploys that exact image to Cloud Run. Using a versioned tag (not `latest`) ensures reproducible rollbacks.

> **Why not `SHORT_SHA`?** `SHORT_SHA` is only populated by git-trigger builds, not `gcloud builds submit`. Use `_TAG` with an explicit version instead.

---

## Key Implementation Notes

### Jackson / LocalDate fix

Spring Boot 4 with Jackson 3 cannot bind `spring.jackson.serialization.write-dates-as-timestamps=false` via properties (enum package moved to `tools.jackson`). Fixed with a `@Bean ObjectMapper` in `JacksonConfig.java` that registers `JavaTimeModule` directly.

### Cloud Run socket connection

[`application-cloudrun.properties`](src/main/resources/application-cloudrun.properties) uses `postgres-socket-factory` for Unix socket connections — no TCP port, no open firewall rules:

```properties
spring.datasource.url=jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory
```

Activated by `SPRING_PROFILES_ACTIVE=cloudrun` set in `cloudbuild.yaml`.

### Dockerfile — three fixes applied

| Fix | Reason |
|-----|--------|
| `COPY build.gradle settings.gradle ./` (trailing `./`) | Without the trailing slash Docker treats the last arg as a filename, not a directory |
| `RUN chmod +x gradlew` | Windows git does not preserve Linux execute bits; Cloud Build fails with exit 126 |
| `RUN ./gradlew bootJar --no-daemon -x test` | No database is available in the Docker build layer — skip tests |
