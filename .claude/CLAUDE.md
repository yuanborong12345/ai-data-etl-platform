# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

基于 Spring Cloud Alibaba 微服务架构的 AI 数据解析与智能分析平台。
全链路数据流：Excel 上传 → ETL 管道解析 → AI 大模型分析 → 结构化报告输出。

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 17 |
| Spring Boot | 3.2.4 |
| Spring Cloud / Spring Cloud Alibaba | 2023.0.1 / 2023.0.1.0 |
| Nacos (注册/配置中心) | 2.x |
| MyBatis-Plus | 3.5.5 |
| MySQL / Redis | 8.0+ / latest |
| RabbitMQ | latest |
| JJWT | 0.12.5 |
| Hutool | 5.38 |
| Knife4j | 4.4.0 |
| Lombok | 1.18.30 |

## Architecture: Module Dependency Chain

This is the most important structural rule to understand:

```
ai-data-model (shared DTOs/entities)      ai-data-common (utilities, responses, exceptions, JWT, constants)
     ─────────── completely parallel, no cross-dependency ───────────
         ↓                                        ↓
     ai-data-service-client (Feign interfaces only — depends on both above)
```

Business services (gateway, user, processor, etc.) explicitly declare their own dependencies to `ai-data-model`, `ai-data-common`, and optionally `ai-data-service-client`. Do NOT rely on transitive dependency resolution.

### Module Roles

- **ai-data-common** (dependency JAR, no main class): Shared infrastructure — `BaseResponse<T>`, `ErrorCode` enum, `BusinessException` + `GlobalExceptionHandler`, `JwtUtils`, `AuthCheck` annotation + `AuthCheckAspect`, `UserContext` (ThreadLocal), `HttpMessageNotReadableException` handler (servlet-only via `@ConditionalOnWebApplication`). Global configs like `JsonConfig` (Long→String serialization) and `AutoWebConfig` (auto-registers `UserContextFilter`, excluded from WebFlux via `@ConditionalOnClass(DispatcherServlet.class)`). Do NOT modify these without explicit approval.

- **ai-data-model** (dependency JAR, no main class): Pure POJOs — `User` entity, DTOs (`UserLoginRequest`, `UserRegisterRequest`, `UserQueryRequest`, `UserSessionDTO`, `GatewaySessionDTO`, `UserDTO`), VOs (`LoginUserVO`, `UserVO`). Entity/VO/DTO layering is strict: never return entity to frontend.

- **ai-data-service-client** (dependency JAR, no main class): Feign interfaces only (`UserFeignClient`). No implementation.

- **ai-data-gateway** (port 8000): **WebFlux (reactive, non-servlet)**. `AuthFilter` (GlobalFilter): whitelist → JWT parse → Redis Session check (kick detection, ban check, sliding renewal <15min → +30min) → `X-User-Id`/`X-User-Role` header passthrough. `CorsConfig` (reactive). Routes: `lb://ai-data-user` → `/api/user/**`, plus intel/monitor routes.

- **ai-data-user** (port 8010, context-path `/api/user`): **Servlet MVC**. Register (SHA-256 salt=`ai_data_user_yuan_salt`) → Login (JWT + Redis Session 30min, key=`aidata:session:user:{userId}`) → Current user → Admin paginated list. `UserMyBatisConfig` for MapperScan + pagination plugin — each service needs its own.

- **ai-data-processor** (port 8020), **ai-data-intelligence** (port 8030), **ai-data-monitor** (port 8040): Skeleton services, no business logic yet. All registered with Nacos.

### Key Cross-Cutting Patterns

| Pattern | What it does |
|---------|-------------|
| `@ComponentScan("com.yuan")` on every business service starter | Loads common module beans (AutoWebConfig, JwtUtils, GlobalExceptionHandler, etc.) |
| `@ConditionalOnClass(DispatcherServlet.class)` on AutoWebConfig | Prevents Filter registration in WebFlux gateway; gateway's AuthFilter is manually defined |
| Gateway `AuthFilter` implements `GlobalFilter` + `Ordered` (order=-1) | Single reactive gateway filter, no servlet filter chain |
| `UserContextFilter` (servlet Filter) | Extracts `X-USER-ROLE` header → `UserContext` ThreadLocal → cleared in `finally` |
| `@AuthCheck(mustRole = "...")` annotation + `AuthCheckAspect` | AOP role check using `UserContext.getRole()` |
| `@ConditionalOnWebApplication(type = SERVLET)` on GlobalExceptionHandler | Prevents duplicate handler registration in non-servlet contexts |

### Database Convention

- Column naming: **camelCase** (not snake_case) — `userAccount`, `createTime`, `isDelete`
- MyBatis-Plus config: `map-underscore-to-camel-case: false` + `table-underline: false`
- Logical delete field: `isDelete` (1=deleted, 0=active)

## Commands

```bash
# Build all modules
mvn clean compile

# Run individual services (each in separate terminal)
mvn spring-boot:run -pl ai-data-gateway       # port 8000
mvn spring-boot:run -pl ai-data-user          # port 8010
mvn spring-boot:run -pl ai-data-processor     # port 8020
mvn spring-boot:run -pl ai-data-intelligence  # port 8030
mvn spring-boot:run -pl ai-data-monitor       # port 8040

# Package
mvn clean package -DskipTests

# Tests
mvn test

# API docs (start gateway + target service first)
# http://localhost:8000/doc.html
```

## Environment Prerequisites

- JDK 17+
- Nacos Server 2.x (default `127.0.0.1:8848`)
- MySQL 8.0+ (database: `ai_data_etl_db`)
- Redis (default `127.0.0.1:6379`)
- RabbitMQ
- SQL init: `source sql/create_table.sql`

## Project Rules

1. **Before any code change, briefly describe the approach and affected files**.

2. **Match existing code style**: naming, annotation density, package structure, exception handling must be consistent with what's already in the codebase.

3. **No new dependencies without discussion**. If needed, add the version in root `pom.xml` `<dependencyManagement>` first.

4. **Global configs are off-limits** without explicit approval: GlobalExceptionHandler, JsonConfig, AuthCheckAspect, AutoWebConfig, root pom.xml version properties, `.gitignore`, gateway AuthFilter whitelist.

5. **When stuck, self-diagnose**: is Nacos/MySQL/Redis running? Is `application-dev.yml` created (`.gitignore`d)? Does MyBatis-Plus column mapping match camelCase DB columns? Does the starter have `@ComponentScan("com.yuan")`?

6. **Sensitive configs** go into `application-dev.yml` (gitignored), NOT into `application.yml` which is tracked.

7. **`application-dev.yml` is `.gitignore`d** — for each service, the tracked `application.yml` references `spring.profiles.active: dev`. You must ensure an `application-dev.yml` exists locally with actual credentials (DB password, JWT secret, etc.) before any service can start.
