# Service Dev — Spring Boot on Google Cloud Run with Cloud SQL PostgreSQL

A complete guide documenting every step taken to deploy a Spring Boot REST API to Google Cloud Run with Cloud SQL PostgreSQL as the database.

---

## Table of Contents

1. [Application Overview](#1-application-overview)
2. [Tech Stack](#2-tech-stack)
3. [Project Structure](#3-project-structure)
4. [Prerequisites](#4-prerequisites)
5. [Google Cloud Account Setup](#5-google-cloud-account-setup)
6. [Fix ADC Quota Project Warning](#6-fix-adc-quota-project-warning)
7. [Enable Required Google Cloud APIs](#7-enable-required-google-cloud-apis)
8. [Create Cloud SQL PostgreSQL Instance](#8-create-cloud-sql-postgresql-instance)
9. [Store Secrets in Secret Manager](#9-store-secrets-in-secret-manager)
10. [Create Artifact Registry](#10-create-artifact-registry)
11. [Build and Push Docker Image](#11-build-and-push-docker-image)
12. [Create Service Account and Grant Permissions](#12-create-service-account-and-grant-permissions)
13. [Deploy to Cloud Run](#13-deploy-to-cloud-run)
14. [Connect DBeaver to Cloud SQL](#14-connect-dbeaver-to-cloud-sql)
15. [Swagger UI — API Documentation and Testing](#15-swagger-ui--api-documentation-and-testing)
16. [API Endpoints Reference](#16-api-endpoints-reference)
17. [Infrastructure Summary](#17-infrastructure-summary)

---

## 1. Application Overview

This is a Spring Boot REST API that performs CRUD operations on a `User` entity, backed by a PostgreSQL database hosted on Google Cloud SQL. The application runs as a containerized service on Google Cloud Run and connects to Cloud SQL using the Cloud SQL Socket Factory (no IP whitelisting required).

**Live URL:**
```
https://service-dev-1007977084712.us-central1.run.app
```

**Swagger UI:**
```
https://service-dev-1007977084712.us-central1.run.app/swagger-ui/index.html
```

---

## 2. Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.1 |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 15 (Cloud SQL) |
| DB Connection | Cloud SQL Socket Factory |
| Container | Docker (multi-stage build) |
| Container Registry | Google Artifact Registry |
| Hosting | Google Cloud Run |
| Secrets | Google Secret Manager |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build Tool | Gradle (Kotlin DSL) |

---

## 3. Project Structure

```
service-dev/
├── src/
│   └── main/
│       ├── java/com/cloud/sql/service_dev/
│       │   ├── ServiceDevApplication.java       # Entry point
│       │   ├── controller/
│       │   │   └── UserController.java          # REST endpoints
│       │   ├── service/
│       │   │   └── UserService.java             # Business logic
│       │   ├── repo/
│       │   │   └── UserRepository.java          # JPA repository
│       │   └── entity/
│       │       └── User.java                    # User entity (id, name)
│       └── resources/
│           └── application.properties           # App configuration
├── Dockerfile                                   # Multi-stage Docker build
├── build.gradle.kts                             # Dependencies
└── README.md
```

---

## 4. Prerequisites

Install the following tools before starting:

### Google Cloud CLI (gcloud)
Download from: https://cloud.google.com/sdk/docs/install

Verify installation:
```powershell
gcloud version
```

### Docker Desktop
Download from: https://www.docker.com/products/docker-desktop/

Verify installation:
```powershell
docker --version
```

### Python 3.11+
Required by gcloud CLI internally.
Download from: https://www.python.org/downloads/

During installation, check **"Add Python to PATH"**, then restart PowerShell.

### DBeaver (Database GUI)
Download from: https://dbeaver.io/download/

### Cloud SQL Auth Proxy
Download from: https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.15.2/cloud-sql-proxy.x64.windows.exe

> Note: The downloaded file may save as `cloud-sql-proxy.exe.exe` — use the exact saved filename when running it.

---

## 5. Google Cloud Account Setup

1. Go to https://cloud.google.com
2. Click **"Get started for free"** — you receive **$300 free credits** for 90 days
3. Sign in with your Google account
4. Fill in billing information (required, but no charges during free tier)
5. Once inside the console, note your **Project ID** shown in the top bar

### Login and Set Project via CLI

```powershell
gcloud auth login
gcloud config set project service-dev-499704
```

---

## 6. Fix ADC Quota Project Warning

After setting the project you may see:

```
WARNING: Your active project does not match the quota project in your local Application Default Credentials file.
```

Fix it by re-authenticating Application Default Credentials:

```powershell
gcloud auth application-default login
gcloud auth application-default set-quota-project service-dev-499704
```

Expected output:
```
Credentials saved to file: [C:\Users\admin\AppData\Roaming\gcloud\application_default_credentials.json]
Quota project "service-dev-499704" was added to ADC which can be used by Google client libraries for billing and quota.
```

---

## 7. Enable Required Google Cloud APIs

```powershell
gcloud services enable `
  run.googleapis.com `
  sqladmin.googleapis.com `
  artifactregistry.googleapis.com `
  cloudbuild.googleapis.com `
  secretmanager.googleapis.com `
  --project=service-dev-499704
```

Expected output:
```
Operation "operations/acf.p2-..." finished successfully.
```

APIs enabled:
| API | Purpose |
|-----|---------|
| `run.googleapis.com` | Cloud Run — serverless container hosting |
| `sqladmin.googleapis.com` | Cloud SQL — managed PostgreSQL |
| `artifactregistry.googleapis.com` | Docker image storage |
| `cloudbuild.googleapis.com` | Cloud build services |
| `secretmanager.googleapis.com` | Secure secret storage |

---

## 8. Create Cloud SQL PostgreSQL Instance

```powershell
gcloud sql instances create service-dev-db `
  --database-version=POSTGRES_15 `
  --tier=db-f1-micro `
  --region=us-central1 `
  --root-password=postgres `
  --project=service-dev-499704
```

Expected output:
```
NAME            DATABASE_VERSION  LOCATION       TIER         PRIMARY_ADDRESS  STATUS
service-dev-db  POSTGRES_15       us-central1-a  db-f1-micro  136.114.167.233  RUNNABLE
```

> No separate database or user creation is needed. The default `postgres` database and `postgres` user are created automatically with the root password set above. This matches the `application.properties` defaults exactly.

### Get Instance Connection Name

```powershell
gcloud sql instances describe service-dev-db --format="value(connectionName)" --project=service-dev-499704
```

Output:
```
service-dev-499704:us-central1:service-dev-db
```

---

## 9. Store Secrets in Secret Manager

Secrets are stored securely and injected into Cloud Run as environment variables at runtime.

**Important:** Write the secret value to a file first to avoid PowerShell adding hidden quotes or newlines:

```powershell
[System.IO.File]::WriteAllText("$env:TEMP\dbsecret.txt", "postgres")
gcloud secrets create db-pass --data-file="$env:TEMP\dbsecret.txt" --project=service-dev-499704
gcloud secrets create db-user --data-file="$env:TEMP\dbsecret.txt" --project=service-dev-499704
gcloud secrets create db-name --data-file="$env:TEMP\dbsecret.txt" --project=service-dev-499704
```

> **Why not use `echo "postgres" | gcloud secrets create ...`?**
> PowerShell's `echo` wraps the value in quotes, storing `"postgres"` instead of `postgres`, which causes password authentication to fail at runtime.

| Secret Name | Value | Maps To |
|-------------|-------|---------|
| `db-pass` | `postgres` | `DB_PASS` env var |
| `db-user` | `postgres` | `DB_USER` env var |
| `db-name` | `postgres` | `DB_NAME` env var |

---

## 10. Create Artifact Registry

Artifact Registry stores your Docker image.

```powershell
gcloud artifacts repositories create service-dev-repo `
  --repository-format=docker `
  --location=us-central1 `
  --project=service-dev-499704
```

Configure Docker to authenticate with Google's registry:

```powershell
gcloud auth configure-docker us-central1-docker.pkg.dev --project=service-dev-499704
```

When prompted, type `Y` to confirm.

---

## 11. Build and Push Docker Image

Make sure **Docker Desktop is running** before building.

### Dockerfile (multi-stage build)

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts .
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Build Image

```powershell
docker build -t us-central1-docker.pkg.dev/service-dev-499704/service-dev-repo/service-dev:latest .
```

> First build takes ~5-6 minutes. Use `--no-cache` flag to force a full rebuild after dependency changes:
> ```powershell
> docker build --no-cache -t us-central1-docker.pkg.dev/service-dev-499704/service-dev-repo/service-dev:latest .
> ```

### Push Image

```powershell
docker push us-central1-docker.pkg.dev/service-dev-499704/service-dev-repo/service-dev:latest
```

---

## 12. Create Service Account and Grant Permissions

Cloud Run needs a dedicated service account with permission to access Cloud SQL and Secret Manager.

### Create Service Account

```powershell
gcloud iam service-accounts create service-dev-sa `
  --display-name="Service Dev SA" `
  --project=service-dev-499704
```

### Grant Cloud SQL Access

```powershell
gcloud projects add-iam-policy-binding service-dev-499704 `
  --member="serviceAccount:service-dev-sa@service-dev-499704.iam.gserviceaccount.com" `
  --role="roles/cloudsql.client"
```

### Grant Secret Manager Access

```powershell
gcloud projects add-iam-policy-binding service-dev-499704 `
  --member="serviceAccount:service-dev-sa@service-dev-499704.iam.gserviceaccount.com" `
  --role="roles/secretmanager.secretAccessor"
```

### Grant Logging Access (for viewing logs in GCP Console)

```powershell
gcloud projects add-iam-policy-binding service-dev-499704 `
  --member="user:hamid.iqbal.khan37@gmail.com" `
  --role="roles/logging.viewer"
```

---

## 13. Deploy to Cloud Run

```powershell
gcloud run deploy service-dev `
  --image=us-central1-docker.pkg.dev/service-dev-499704/service-dev-repo/service-dev:latest `
  --region=us-central1 `
  --platform=managed `
  --allow-unauthenticated `
  --service-account=service-dev-sa@service-dev-499704.iam.gserviceaccount.com `
  --add-cloudsql-instances=service-dev-499704:us-central1:service-dev-db `
  --set-env-vars="INSTANCE_CONNECTION_NAME=service-dev-499704:us-central1:service-dev-db" `
  --set-secrets="DB_PASS=db-pass:latest,DB_USER=db-user:latest,DB_NAME=db-name:latest" `
  --memory=512Mi `
  --cpu=1 `
  --port=8080 `
  --project=service-dev-499704
```

Expected output:
```
Service [service-dev] revision [service-dev-00002-fpv] has been deployed and is serving 100 percent of traffic.
Service URL: https://service-dev-1007977084712.us-central1.run.app
```

### Redeployment (after code changes)

After any code change, rebuild the image with `--no-cache`, push, then redeploy with a simplified command:

```powershell
docker build --no-cache -t us-central1-docker.pkg.dev/service-dev-499704/service-dev-repo/service-dev:latest .
docker push us-central1-docker.pkg.dev/service-dev-499704/service-dev-repo/service-dev:latest
gcloud run deploy service-dev `
  --image=us-central1-docker.pkg.dev/service-dev-499704/service-dev-repo/service-dev:latest `
  --region=us-central1 `
  --project=service-dev-499704
```

---

## 14. Connect DBeaver to Cloud SQL

Cloud SQL does not accept direct TCP connections from the internet. Use the **Cloud SQL Auth Proxy** to create a secure local tunnel.

### Step 1 — Run the Proxy

Open a **dedicated PowerShell window** and run (keep it open):

```powershell
& "C:\Users\admin\Downloads\cloud-sql-proxy.exe.exe" --port=5433 service-dev-499704:us-central1:service-dev-db
```

Expected output:
```
2026/06/17 11:28:16 Authorizing with Application Default Credentials
2026/06/17 11:28:16 [service-dev-499704:us-central1:service-dev-db] Listening on 127.0.0.1:5433
2026/06/17 11:28:16 The proxy has started successfully and is ready for new connections!
```

> Keep this window open whenever you use DBeaver. Closing it drops the database connection.

### Step 2 — Connect in DBeaver

1. Open DBeaver
2. Click **New Database Connection** (plug `+` icon)
3. Select **PostgreSQL** → click **Next**
4. Enter connection details:

| Field | Value |
|-------|-------|
| Host | `127.0.0.1` |
| Port | `5433` |
| Database | `postgres` |
| Username | `postgres` |
| Password | `postgres` |

5. Click **Test Connection** — download the PostgreSQL driver if prompted
6. Click **Finish**

### Step 3 — Browse Data

Navigate in the left panel:
```
postgres → Schemas → public → Tables → users
```

Right-click `users` → **View Data** to see all rows.

---

## 15. Swagger UI — API Documentation and Testing

### Dependency Added

The following dependency was added to `build.gradle.kts`:

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
```

### Access Swagger UI

Open in your browser:
```
https://service-dev-1007977084712.us-central1.run.app/swagger-ui/index.html
```

> The root URL `/` returns a Whitelabel 404 page — this is normal. Always navigate directly to `/swagger-ui/index.html`.

### How to Test in Swagger UI

1. Open the Swagger URL above
2. Click on any endpoint (e.g. `POST /api/users`) to expand it
3. Click **"Try it out"**
4. Fill in the request body or parameters
5. Click **"Execute"**
6. View the response below

---

## 16. API Endpoints Reference

Base URL: `https://service-dev-1007977084712.us-central1.run.app`

---

### POST /api/users — Create a User

**Request Body:**
```json
{
  "name": "John Doe"
}
```

**PowerShell:**
```powershell
Invoke-WebRequest -UseBasicParsing -Method POST `
  -Uri "https://service-dev-1007977084712.us-central1.run.app/api/users" `
  -Headers @{"Content-Type"="application/json"} `
  -Body '{"name":"John Doe"}'
```

**Success Response (200):**
```json
{
  "id": 1,
  "name": "John Doe"
}
```

---

### GET /api/users — Get All Users

**PowerShell:**
```powershell
Invoke-WebRequest -UseBasicParsing `
  -Uri "https://service-dev-1007977084712.us-central1.run.app/api/users"
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "name": "John Doe"
  },
  {
    "id": 2,
    "name": "Jane Doe"
  }
]
```

---

### GET /api/users/{id} — Get User by ID

**PowerShell:**
```powershell
Invoke-WebRequest -UseBasicParsing `
  -Uri "https://service-dev-1007977084712.us-central1.run.app/api/users/1"
```

**Success Response (200):**
```json
{
  "id": 1,
  "name": "John Doe"
}
```

**Error Response (500 — user not found):**
```json
{
  "message": "User not found"
}
```

---

### PUT /api/users/{id} — Update a User

**Request Body:**
```json
{
  "name": "John Updated"
}
```

**PowerShell:**
```powershell
Invoke-WebRequest -UseBasicParsing -Method PUT `
  -Uri "https://service-dev-1007977084712.us-central1.run.app/api/users/1" `
  -Headers @{"Content-Type"="application/json"} `
  -Body '{"name":"John Updated"}'
```

**Success Response (200):**
```json
{
  "id": 1,
  "name": "John Updated"
}
```

---

### DELETE /api/users/{id} — Delete a User

**PowerShell:**
```powershell
Invoke-WebRequest -UseBasicParsing -Method DELETE `
  -Uri "https://service-dev-1007977084712.us-central1.run.app/api/users/1"
```

**Success Response (200):**
```
User deleted successfully
```

---

## 17. Infrastructure Summary

| Component | Details |
|-----------|---------|
| **Project ID** | `service-dev-499704` |
| **Region** | `us-central1` |
| **Cloud Run Service** | `service-dev` |
| **Service URL** | `https://service-dev-1007977084712.us-central1.run.app` |
| **Swagger UI** | `https://service-dev-1007977084712.us-central1.run.app/swagger-ui/index.html` |
| **Cloud SQL Instance** | `service-dev-db` (PostgreSQL 15, db-f1-micro) |
| **Instance Connection Name** | `service-dev-499704:us-central1:service-dev-db` |
| **Database Name** | `postgres` |
| **DB User** | `postgres` |
| **Artifact Registry** | `us-central1-docker.pkg.dev/service-dev-499704/service-dev-repo/service-dev` |
| **Service Account** | `service-dev-sa@service-dev-499704.iam.gserviceaccount.com` |
| **Secrets** | `db-pass`, `db-user`, `db-name` (Secret Manager) |

### Cost Estimate

| Service | Free Tier / Cost |
|---------|-----------------|
| Cloud Run | 2 million requests/month free, scales to zero |
| Cloud SQL db-f1-micro | ~$7/month (not free tier) |
| Artifact Registry | 500 MB storage free |
| Secret Manager | 6 active secret versions free |

---

## application.properties Configuration

```properties
spring.application.name=service-dev

spring.datasource.url=jdbc:postgresql:///${DB_NAME:postgres}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME:}&socketFactory=com.google.cloud.sql.postgres.SocketFactory
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASS:postgres}

spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

The environment variables `DB_NAME`, `DB_USER`, `DB_PASS`, and `INSTANCE_CONNECTION_NAME` are injected at runtime from Secret Manager and Cloud Run environment variables — no hardcoded credentials in the codebase.
