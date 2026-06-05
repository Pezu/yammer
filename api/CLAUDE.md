# Yammer API

Spring Boot REST API for the Yammer project.

## Stack

- **Java 21** (Amazon Corretto)
- **Spring Boot 3.2.1** (managed via `spring-boot-starter-parent`)
- **Maven** (`mvn`, v3.9.x) — there is no Gradle setup
- **PostgreSQL** via Spring Data JPA (`ddl-auto: validate` — schema owned by Flyway, not Hibernate)
- **Flyway 10.x** for migrations (note: 10.x split per-DB modules out of `flyway-core`, so `flyway-database-postgresql` is an explicit dependency)
- **JWT** via jjwt (`io.jsonwebtoken`, HS256); bean validation via `spring-boot-starter-validation`
- **Lombok** for boilerplate — prefer it where it fits (see Conventions)
- Group ID: `com.servio`, artifact: `yammer`, base package: **`com.yammer`**

## Layout

```
api/
├── pom.xml
├── src/main/java/com/yammer/
│   ├── YammerApplication.java        # @SpringBootApplication entry point
│   ├── controller/                   # @RestController classes (HelloController, AuthController)
│   ├── dto/                          # request/response records (LoginRequest, LoginResponse)
│   ├── entity/                       # JPA entities (UserEntity)
│   ├── repository/                   # Spring Data repos (UserRepository)
│   └── service/                      # business logic (AuthService, JwtService)
├── src/main/resources/
│   ├── application.yml               # config (datasource, JPA, Flyway, JWT, actuator)
│   └── db/migration/                 # Flyway migrations (V<n>__*.sql)
│       ├── V1__init.sql              # role, client, users tables
│       ├── V2__seed_roles.sql        # WAITER/BARMAN/ADMIN/SERVICE/SUPER
│       └── V3__seed_admin.sql        # admin user (md5 password)
└── src/test/java/com/yammer/
    └── YammerApplicationTests.java   # context-load smoke test (needs a DB)
```

## Commands

Run all from the `api/` directory.

- Build: `mvn -DskipTests package`
- Run tests: `mvn test`
- Run locally: `mvn spring-boot:run` (serves on http://localhost:8080)
- Run the jar: `java -jar target/yammer-0.0.1-SNAPSHOT.jar`

## Endpoints

- `GET /` → plain-text health string ("Yammer API is running")
- `GET /actuator/health` → Spring Boot Actuator health check
- `POST /auth/login` → authenticate and get a JWT
  - Request: `{ "username": "...", "password": "..." }`
  - Response `200`: `{ "token": "<jwt>", "username": "...", "roles": ["ADMIN", ...] }`
  - `401` on bad credentials, `400` on a blank username/password
  - The JWT (`AuthService` → `JwtService`) is HS256-signed; `sub` is the username
    and the `roles` claim carries the user's roles array.

## Authentication

- Passwords are stored as **MD5 hex** (matches Postgres `md5()`); `AuthService`
  hashes the submitted password with `DigestUtils.md5DigestAsHex` and compares.
  ⚠️ MD5 is not secure for passwords — migrate to BCrypt before production.
- JWT config in `application.yml` under `jwt.*`: `JWT_SECRET` (>= 32 bytes for
  HS256) and `JWT_EXPIRATION_MS` (default 24h). The dev default secret is a
  placeholder — override it everywhere real.

### Authorization (Spring Security)

The model is two-tier: **SUPER** = access to everything, no tenant scope
(`client_id = null`); everyone else (e.g. **ADMIN**) has full rights but only
within their **own client**.

- **Identity:** the JWT carries `sub` (username), `roles`, and `clientId`
  (omitted for SUPER). `security/JwtAuthFilter` parses it into a typed
  `security/UserPrincipal` (`username`, `clientId`, `roles`, `isSuper()`) set as
  the authentication principal, with `ROLE_<name>` authorities. No per-request DB
  lookup — identity comes entirely from the token.
- **`security/CurrentUserProvider.require()`** is the single access point services
  use to get the `UserPrincipal` (401 if absent).
- **`SecurityConfig`** is stateless and `@EnableMethodSecurity`: only `permitAll`
  for `POST /auth/login`, `/actuator/**`, `GET /`; everything else `authenticated()`.
  Fine-grained rules live on the methods.
- **Client writes are SUPER-only** via `@PreAuthorize("hasRole('SUPER')")` on
  `ClientController` create/update/delete. `GET /clients` is authenticated but
  `ClientService.list()` scopes results (SUPER → all, others → their own client
  from the principal).
- **User tenant scoping (`UserService`):** SUPER sees/edits all users; a non-SUPER
  operator only sees/edits users in their own client (cross-tenant ids return 404),
  cannot touch SUPER users, cannot grant the SUPER role, and any user they create/
  edit is forced onto *their own* client (requested `clientId` is ignored).
- To add a new tenant-scoped resource, resolve the caller via `CurrentUserProvider`
  and branch on `isSuper()` / `clientId()` the same way — keep scoping in the
  service layer. (If tenant tables proliferate, consider Hibernate `@TenantId` or
  Postgres RLS so scoping can't be forgotten.)
- The seeded `admin` user is promoted to **SUPER** in `V4__Add_user_client_link.sql`.

## Database

- Local defaults (override via env vars): db `yammer`, schema `yammer`,
  user `yammer` / `yammerpass` on `localhost:5432`.
  Connection vars: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`.
- Spin up a local Postgres with `docker compose up -d` (see `docker-compose.yml`).
  `init-schemas.sql` creates the `yammer` schema on first start.
- Flyway runs migrations on startup against the `yammer` schema.

### Schema (V1__init.sql)

| Table    | Columns                                                              |
|----------|---------------------------------------------------------------------|
| `role`   | `id` UUID PK, `role` VARCHAR UNIQUE                                  |
| `client` | `id` UUID PK, `name`, `phone`, `email`                              |
| `users`  | `id` UUID PK, `username` UNIQUE, `password`, `phone`, `email`, `roles` TEXT[] |

The users table is named `users` (not `user`) because `user` is a reserved word
in Postgres.

Later migrations add tenant columns/tables:
- `V4` — `users.client_id` (FK → `client`); seeded `admin` promoted to SUPER.
- `V5` — `location` (`id` UUID PK, `name`, `client_id` FK → `client`). Client-owned,
  scoped exactly like users (see *Authorization*); `LocationService`/`LocationController`
  follow the new-resource checklist. `GET /locations?clientId=<id>` filters to one client
  (used by the menu page's location dropdown).
- `V6` — `menu` (`id`, `location_id` FK → `location`, `name`, `created_at`) and
  `menu_item` (`id`, `menu_id` FK, `parent_id` self-FK, `name`, `orderable`, `price`,
  `sort_order`). A menu is a tree of items: categories (`orderable=false`) and products
  (`orderable=true` + `price`). `MenuService` access is via the menu's location's client
  (SUPER any, others own → 404 otherwise). `PUT /menu/menus/{id}/tree` replaces the whole
  tree (delete-all then insert); `GET .../tree` returns it nested.
- `V7`/`V9` — `vat_type` (`id`, `value` numeric — `name` was dropped in V9; a VAT type
  is just its percentage), global catalog data (not client-scoped). `/vat-types`: read for
  any authenticated user, writes SUPER-only (like roles). `V7` also adds
  `menu_item.vat_type_id` (nullable FK) so a product can carry a VAT type.
- `V8` — widened `menu_item.name` to `TEXT` (it now stores rich-text/HTML).

## Conventions

- Controllers go in `com.yammer.controller`, suffixed `Controller`; services in
  `com.yammer.service`, entities in `com.yammer.entity`, repos in
  `com.yammer.repository`, request/response records in `com.yammer.dto`.
- **Use Lombok where it fits**: `@RequiredArgsConstructor` for constructor
  injection of collaborator beans (drop hand-written constructors),
  `@Getter`/`@Setter` on entities, `@Slf4j` for loggers. DTOs are `record`s — no
  Lombok.
- For `@Value`-injected config, prefer field injection + a `@PostConstruct` for
  any derived state (see `JwtService`, which builds its `SecretKey` from
  `jwt.secret` in `init()`), rather than `@Value` constructor params.
- Config lives in `application.yml`; add profile files (`application-<profile>.yml`)
  as environments are introduced.
- **Never edit an existing Flyway migration** — always add a new `V<n>__*.sql`
  under `src/main/resources/db/migration`. Hibernate is `validate`-only, so the
  Java entities and the migrations must stay in sync.

### Building a new endpoint/resource — authorization checklist

Every new API MUST fit the SUPER / per-client model (see *Authorization* above).
Don't bolt on auth ad hoc — follow this:

1. **Identity comes from the token, not the DB.** Resolve the caller with
   `CurrentUserProvider.require()` → `UserPrincipal` (`isSuper()`, `clientId()`,
   `roles()`). Never re-query the user just to learn their roles/client.
2. **Coarse rule on the method.** Put role gates on the controller with
   `@PreAuthorize("hasRole('SUPER')")` (note: authorities are `ROLE_`-prefixed, so
   use `hasRole`, not `hasAuthority`). Keep `SecurityConfig` URL rules minimal.
3. **Tenant-scope in the service, always.** For any client-owned data:
   - reads: SUPER → all; otherwise filter by `me.clientId()` (e.g. a
     `findByClientId...` repo method); a non-SUPER caller with `clientId == null`
     gets nothing.
   - get/update/delete by id: load it, then if `!me.isSuper()` and the row's
     `clientId != me.clientId()` throw **404** (hide cross-tenant existence).
   - writes: force the row's `clientId` to `me.clientId()` for non-SUPER callers —
     never trust a client id from the request body; a SUPER caller may set any
     existing client.
   - never let a non-SUPER caller escalate (e.g. grant the SUPER role).
4. **New client-owned table** → add a `client_id UUID REFERENCES client(id)` column
   (new `V<n>` migration) and a `clientId` field on the entity; mirror the
   `UserService` patterns for scoping.
5. Reserve cross-tenant/global operations for SUPER.

This keeps every resource consistent. If client-owned tables multiply, graduate to
Hibernate `@TenantId` or Postgres RLS so scoping is enforced automatically rather
than by convention.

## Notes for the future

- This started as a minimal web + actuator app. Add starters (security,
  validation, websocket, etc.) to `pom.xml` as features are built.
