# Challenge 2 — The Patient: Spring Music Monolith Diagnosis

> **Context:** Northwind Logistics catalog service. Spring Boot 2.4.0, Java 8, Cloud Foundry.
> This document describes the current state of the system before any modernization work begins.
> Its purpose is to give every team member a shared, honest picture of what we're dealing with.

---

## What the System Does

A single-purpose REST API that manages a catalog of music albums. One entity (`Album`), one REST controller, one frontend (AngularJS). The same codebase targets three different database backends — relational (H2/MySQL/PostgreSQL/SQL Server), MongoDB, and Redis — selected at startup via Spring Profiles.

**Endpoints exposed:**

| Method | Path | What it does |
|--------|------|--------------|
| GET | `/albums` | Return all albums |
| GET | `/albums/{id}` | Return one album |
| PUT | `/albums` | Create album |
| POST | `/albums` | Update album |
| DELETE | `/albums/{id}` | Delete album |
| GET | `/appinfo` | Active profiles + CF service names |
| GET | `/service` | Cloud Foundry service details |
| GET | `/errors/kill` | **Calls System.exit(1)** |
| GET | `/errors/fill-heap` | **Infinite memory allocation loop** |
| GET | `/errors/throw` | **Throws NullPointerException** |

---

## Source Map

```
src/main/java/org/cloudfoundry/samples/music/
├── Application.java                          ← entry point, wires the initializer
├── config/
│   ├── SpringApplicationContextInitializer  ← detects CF, sets profiles, excludes auto-config
│   └── data/
│       └── RedisConfig                      ← JSON serialization for Redis
├── domain/
│   ├── Album                                ← the only entity
│   ├── ApplicationInfo                      ← DTO for /appinfo
│   └── RandomIdGenerator                    ← custom Hibernate UUID generator
├── repositories/
│   ├── AlbumRepositoryPopulator             ← seeds data on ApplicationReadyEvent
│   ├── jpa/JpaAlbumRepository               ← Spring Data JPA (default + mysql + postgres)
│   ├── mongodb/MongoAlbumRepository         ← Spring Data MongoDB
│   └── redis/RedisAlbumRepository           ← manual CrudRepository implementation
└── web/
    ├── AlbumController                      ← CRUD REST, profile-agnostic
    ├── ErrorController                      ← intentional crash endpoints
    └── InfoController                       ← runtime info + CF service details
```

**Total: 13 Java classes, 1 entity, 3 persistence backends, 1 frontend (AngularJS 1.2.16).**

---

## What Makes This Hard to Evolve

### 1. Hidden business logic in an event listener

[`AlbumRepositoryPopulator`](../src/main/java/org/cloudfoundry/samples/music/repositories/AlbumRepositoryPopulator.java) implements `ApplicationListener<ApplicationReadyEvent>`. It seeds the database after startup by reading `albums.json` — but nobody looking at the domain layer would find it there. The condition `count() == 0` is an implicit business rule buried in infrastructure code.

**Risk:** anyone modifying startup behaviour or migration logic will miss this unless they know where to look.

---

### 2. God class in configuration

[`SpringApplicationContextInitializer`](../src/main/java/org/cloudfoundry/samples/music/config/SpringApplicationContextInitializer.java) does four unrelated things in one class:
- Detects Cloud Foundry bound services via `CfEnv`
- Maps service tags to Spring profiles (hardcoded `HashMap`)
- Validates mutual exclusion of profiles (throws `IllegalStateException`)
- Excludes auto-configurations dynamically at bootstrap

**Risk:** any change to infrastructure (new DB, new cloud platform) requires editing this class. The four concerns are tangled — changing profile logic risks breaking CF detection.

---

### 3. Manual repository implementation

[`RedisAlbumRepository`](../src/main/java/org/cloudfoundry/samples/music/repositories/redis/RedisAlbumRepository.java) manually implements `CrudRepository<Album, String>` — 111 lines of boilerplate including ID generation, hash operations, and type conversion. The other two repositories (JPA, MongoDB) are 3-line Spring Data interfaces.

**Risk:** Redis behaviour diverges silently from JPA/MongoDB. Already has a subtle difference: `findAll()` returns an empty list rather than throwing on missing key, which is not how the other repositories behave.

---

### 4. Dependency created by hand, not injected

[`InfoController`](../src/main/java/org/cloudfoundry/samples/music/web/InfoController.java) does `new CfEnv()` directly in its constructor. This is untestable — you cannot inject a mock, you cannot swap the implementation, and it binds the controller to Cloud Foundry at the class level.

```java
// InfoController.java
public InfoController(Environment spring) {
    this.cfEnv = new CfEnv();   // ← cannot be injected or mocked
    ...
}
```

---

### 5. Dangerous endpoints with no authentication

[`ErrorController`](../src/main/java/org/cloudfoundry/samples/music/web/ErrorController.java) exposes three HTTP endpoints that kill or destabilise the running process — no auth, no rate limiting, no feature flag:

```java
@RequestMapping("/errors/kill")
public void kill() {
    System.exit(1);              // terminates the JVM
}

@RequestMapping("/errors/fill-heap")
public void fillHeap() {
    while (true) {
        junk.add(new int[1024 * 1024 * 9]);   // infinite OOM loop
    }
}
```

**Risk:** reachable by anyone who can send HTTP requests. Must be removed before any production deployment.

---

### 6. All Actuator endpoints exposed publicly

`application.yml` contains:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
```

This exposes `/actuator/env` (environment variables, credentials), `/actuator/beans` (full Spring context), `/actuator/heapdump`, and others — publicly, with no authentication.

---

### 7. PUT and POST do the same thing

`AlbumController` uses `PUT /albums` to create and `POST /albums` to update — but both call `repository.save()` with no distinction. HTTP semantics are inverted.

```java
@RequestMapping(method = RequestMethod.PUT)
public Album add(@Valid @RequestBody Album album) {
    return repository.save(album);
}

@RequestMapping(method = RequestMethod.POST)
public Album update(@Valid @RequestBody Album album) {
    return repository.save(album);   // identical to add()
}
```

---

### 8. No test coverage

The only test file is `ApplicationTests.java` — it loads the Spring context and asserts nothing. There are no unit tests, no integration tests, no contract tests. Any change to business logic is invisible to automated verification.

---

### 9. Outdated technology stack

| Component | Current version | Status |
|-----------|----------------|--------|
| Spring Boot | 2.4.0 (Nov 2020) | End of OSS support |
| Java target | 8 | Should be 17+ (LTS) |
| AngularJS | 1.2.16 | End of life Dec 2021 |
| Hibernate dialect | `MySQL55Dialect`, `ProgressDialect` | Deprecated in Hibernate 6 |

---

## Dependency Map

```
HTTP client
    │
    ▼
AlbumController
    │ CrudRepository<Album, String>
    ├──► JpaAlbumRepository       (profile: default / mysql / postgres / sqlserver)
    ├──► MongoAlbumRepository     (profile: mongodb)
    └──► RedisAlbumRepository     (profile: redis)
             └──► RedisConfig (Jackson serialization)

SpringApplicationContextInitializer
    ├──► CfEnv (Cloud Foundry service detection)
    ├──► Profile activation
    └──► Auto-configuration exclusion

AlbumRepositoryPopulator
    ├──► ApplicationReadyEvent (lifecycle hook)
    ├──► CrudRepository (any active backend)
    └──► albums.json (seed data)

InfoController
    └──► new CfEnv() (direct instantiation — not injected)

ErrorController
    └──► System.exit() / infinite OOM loop (unauthenticated HTTP endpoints)
```

---

## Summary: What to Fix Before Extracting Anything

| Problem | Severity | Blocks extraction? |
|---------|----------|--------------------|
| Business logic in event listener | Medium | Yes — seeding logic must move |
| God class in initializer | Medium | Yes — profile logic is a seam |
| Manual Redis repository | Low | No — isolated to one backend |
| Untestable InfoController | Low | No |
| Dangerous error endpoints | **High** | Must remove before any deployment |
| Exposed Actuator endpoints | **High** | Must restrict before any deployment |
| No test coverage | **High** | Yes — characterization tests needed first |
| Outdated stack | Medium | No — can modernize in parallel |
