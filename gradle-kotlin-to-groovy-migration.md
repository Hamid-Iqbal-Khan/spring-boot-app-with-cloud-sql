# Gradle Migration: Kotlin DSL → Groovy DSL

> **Branch:** `gradle-with-groovy`
> **Migrated from:** `feature/cloud-run` (Kotlin DSL)
> **Date:** 2026-06-23

---

## Table of Contents

1. [Why Migrate?](#1-why-migrate)
2. [Files Changed](#2-files-changed)
3. [Syntax Differences: Side-by-Side](#3-syntax-differences-side-by-side)
4. [Section-by-Section Migration](#4-section-by-section-migration)
5. [Lockfile Regeneration](#5-lockfile-regeneration)
6. [Verification](#6-verification)
7. [Quick Reference Cheat Sheet](#7-quick-reference-cheat-sheet)

---

## 1. Why Migrate?

| Reason | Detail |
|--------|--------|
| Team preference | Groovy DSL is the original Gradle scripting language and is more widely documented in older tutorials and legacy projects |
| Familiarity | Groovy syntax is closer to Java and feels more natural for Java developers who are new to Gradle |
| Tooling compatibility | Some older Gradle plugins and IDE versions have better Groovy DSL support |
| Consistency | Aligning with other projects in the organisation that use Groovy DSL |

> **Note:** Both DSLs produce identical build behaviour. The choice is purely stylistic and organisational.

---

## 2. Files Changed

| File | Action | Description |
|------|--------|-------------|
| `build.gradle.kts` | **Deleted** | Kotlin DSL build script |
| `settings.gradle.kts` | **Deleted** | Kotlin DSL settings file |
| `build.gradle` | **Created** | Groovy DSL build script (equivalent content) |
| `settings.gradle` | **Created** | Groovy DSL settings file |
| `gradle.lockfile` | **Regenerated** | Dependency lock file re-created from new `build.gradle` |

No Java source files, test files, properties files, or any other project files were modified. This was a pure build-script migration.

---

## 3. Syntax Differences: Side-by-Side

### String Literals

```kotlin
// Kotlin DSL — double quotes only
id("org.springframework.boot") version "4.1.0"
group = "com.cloud.sql"
```

```groovy
// Groovy DSL — single quotes for static strings, double quotes when interpolating
id 'org.springframework.boot' version '4.1.0'
group = 'com.cloud.sql'
```

> **Rule:** In Groovy, use single quotes `'...'` for plain strings. Use double quotes `"..."` only when the string contains a `${variable}` interpolation.

---

### Plugin Declaration

```kotlin
// Kotlin DSL — parentheses required
plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.4"
    id("org.sonarqube") version "6.2.0.5505"
    jacoco
}
```

```groovy
// Groovy DSL — no parentheses, id keyword with single quotes
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'com.diffplug.spotless' version '7.0.4'
    id 'org.sonarqube' version '6.2.0.5505'
    id 'jacoco'
}
```

> **Note:** In Kotlin DSL, built-in plugins like `java` and `jacoco` can be declared without `id(...)`. In Groovy DSL, all plugins use `id '...'`.

---

### Variable Declaration

```kotlin
// Kotlin DSL — val keyword (immutable)
val springdocVersion = "2.8.9"
val cloudSqlSocketFactoryVersion = "1.21.0"
```

```groovy
// Groovy DSL — def keyword
def springdocVersion = '2.8.9'
def cloudSqlSocketFactoryVersion = '1.21.0'
```

---

### Variable Interpolation in Strings

```kotlin
// Kotlin DSL — $variable or ${expression}
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
implementation("com.google.cloud.sql:postgres-socket-factory:$cloudSqlSocketFactoryVersion")
```

```groovy
// Groovy DSL — ${variable} inside double-quoted strings
implementation "org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdocVersion}"
implementation "com.google.cloud.sql:postgres-socket-factory:${cloudSqlSocketFactoryVersion}"
```

---

### Dependency Declarations

```kotlin
// Kotlin DSL — method calls with parentheses and double-quoted strings
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

```groovy
// Groovy DSL — no parentheses, single-quoted strings
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

---

### Task Configuration — `withType`

```kotlin
// Kotlin DSL — generic type parameter in angle brackets
tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
```

```groovy
// Groovy DSL — type passed as a class argument
tasks.withType(Test) {
    useJUnitPlatform()
    finalizedBy jacocoTestReport
}
```

---

### Task Configuration — `jacocoTestReport`

```kotlin
// Kotlin DSL — tasks.jacocoTestReport with typed reference
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
```

```groovy
// Groovy DSL — direct task name reference
jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}
```

---

### Sonar Properties

```kotlin
// Kotlin DSL — property() function calls with parentheses
sonar {
    properties {
        property("sonar.projectKey", "spring-boot-app-with-cloud-sql")
        property("sonar.organization", "spring-boot-app-with-cloud-sq")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.exclusions", "**/config/**,**/dto/**,**/entity/**")
    }
}
```

```groovy
// Groovy DSL — property keyword without parentheses; double quotes for interpolated string
sonar {
    properties {
        property 'sonar.projectKey', 'spring-boot-app-with-cloud-sql'
        property 'sonar.organization', 'spring-boot-app-with-cloud-sq'
        property 'sonar.host.url', 'https://sonarcloud.io'
        property 'sonar.coverage.jacoco.xmlReportPaths', "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml"
        property 'sonar.exclusions', '**/config/**,**/dto/**,**/entity/**'
    }
}
```

---

### settings file

```kotlin
// settings.gradle.kts — Kotlin DSL
rootProject.name = "spring-boot-app-with-cloud-sql"
```

```groovy
// settings.gradle — Groovy DSL
rootProject.name = 'spring-boot-app-with-cloud-sql'
```

Only the quote style changes. Everything else is identical.

---

## 4. Section-by-Section Migration

### Complete `build.gradle.kts` (before)

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.4"
    id("org.sonarqube") version "6.2.0.5505"
    jacoco
}

group = "com.cloud.sql"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

sonar {
    properties {
        property("sonar.projectKey", "spring-boot-app-with-cloud-sql")
        property("sonar.organization", "spring-boot-app-with-cloud-sq")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.exclusions", "**/config/**,**/dto/**,**/entity/**")
    }
}

val springdocVersion = "2.8.9"
val cloudSqlSocketFactoryVersion = "1.21.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("com.google.cloud.sql:postgres-socket-factory:$cloudSqlSocketFactoryVersion")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
```

---

### Complete `build.gradle` (after)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'com.diffplug.spotless' version '7.0.4'
    id 'org.sonarqube' version '6.2.0.5505'
    id 'jacoco'
}

group = 'com.cloud.sql'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

sonar {
    properties {
        property 'sonar.projectKey', 'spring-boot-app-with-cloud-sql'
        property 'sonar.organization', 'spring-boot-app-with-cloud-sq'
        property 'sonar.host.url', 'https://sonarcloud.io'
        property 'sonar.coverage.jacoco.xmlReportPaths', "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml"
        property 'sonar.exclusions', '**/config/**,**/dto/**,**/entity/**'
    }
}

def springdocVersion = '2.8.9'
def cloudSqlSocketFactoryVersion = '1.21.0'

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    implementation "org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdocVersion}"
    implementation "com.google.cloud.sql:postgres-socket-factory:${cloudSqlSocketFactoryVersion}"

    runtimeOnly 'org.postgresql:postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.withType(Test) {
    useJUnitPlatform()
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}
```

---

## 5. Lockfile Regeneration

After replacing `build.gradle.kts` with `build.gradle`, the existing `gradle.lockfile` was stale because it was generated from the Kotlin DSL build. It was regenerated by running:

```bash
./gradlew dependencies --write-locks
```

Output confirmed:
```
Persisted dependency lock state for root project 'spring-boot-app-with-cloud-sql'
BUILD SUCCESSFUL in 4s
```

The resolved dependency versions did not change — only the build file format changed.

---

## 6. Verification

After migration, the full build was verified:

```bash
./gradlew clean build
```

```
> Task :compileJava
> Task :spotlessJavaCheck
> Task :test              ← 19 tests, 0 failures
> Task :jacocoTestReport
> Task :bootJar
> Task :build

BUILD SUCCESSFUL in 40s
12 actionable tasks: 12 executed
```

All 19 tests passed:

| Test Class | Tests | Result |
|------------|-------|--------|
| `UserControllerTest` | 6 | PASSED |
| `UserRowMapperTest` | 2 | PASSED |
| `UserServiceTest` | 11 | PASSED |

---

## 7. Quick Reference Cheat Sheet

| Concept | Kotlin DSL (`.kts`) | Groovy DSL |
|---------|--------------------|---------  |
| Plugin (built-in) | `java` | `id 'java'` |
| Plugin (external) | `id("x.y.z") version "1.0"` | `id 'x.y.z' version '1.0'` |
| Variable | `val x = "value"` | `def x = 'value'` |
| String (static) | `"value"` | `'value'` |
| String (interpolated) | `"prefix-$var"` | `"prefix-${var}"` |
| Dependency | `implementation("group:artifact")` | `implementation 'group:artifact'` |
| Task by type | `tasks.withType<Test> { }` | `tasks.withType(Test) { }` |
| Named task | `tasks.jacocoTestReport { }` | `jacocoTestReport { }` |
| Task reference | `finalizedBy(tasks.jacocoTestReport)` | `finalizedBy jacocoTestReport` |
| Task dependency | `dependsOn(tasks.test)` | `dependsOn test` |
| Method call | `property("key", "value")` | `property 'key', 'value'` |
| Settings file | `settings.gradle.kts` | `settings.gradle` |
| Build file | `build.gradle.kts` | `build.gradle` |

---

*Migration performed on branch `gradle-with-groovy`. Source branch: `feature/cloud-run`.*
