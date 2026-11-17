# BorrowBuddy API deployment guide

This repository contains the **Spring Boot API** for BorrowBuddy. It serves the
REST endpoints, the JWT auth flow, the QR handover tokens and the uploaded
images. The React UI lives in the separate
[borrow-buddy-frontend](https://github.com/Harshavardhan-15/borrow-buddy-frontend)
repository and is deployed to Vercel.

```
Browser ──► Vercel (React SPA)
                │  fetch + Bearer JWT
                ▼
        Render (Spring Boot API) ──► Managed MySQL (Aiven / Render)
```

This guide covers the backend half: MySQL provisioning, the Render service, the
Docker image, environment variables, image storage and go-live checks.

---

## Contents

1. [Prerequisites](#prerequisites)
2. [Step 1 — Push this repository to GitHub](#step-1--push-this-repository-to-github)
3. [Step 2 — Provision MySQL](#step-2--provision-mysql)
4. [Step 3 — Deploy the API to Render](#step-3--deploy-the-api-to-render)
5. [Step 4 — Connect the frontend](#step-4--connect-the-frontend)
6. [Step 5 — Verify the deployment](#step-5--verify-the-deployment)
7. [Docker Compose (self-hosted)](#docker-compose-self-hosted)
8. [Environment variable reference](#environment-variable-reference)
9. [Image storage](#image-storage)
10. [Scaling and Redis](#scaling-and-redis)
11. [Production hardening checklist](#production-hardening-checklist)
12. [Database migrations](#database-migrations)
13. [Troubleshooting](#troubleshooting)

---

## Prerequisites

- A GitHub account and this repository pushed to it
- A Render account (the free tier is fine for a demo)
- A managed MySQL 8 database (Aiven, Render MySQL, PlanetScale, Railway, …)
- Optional: Docker for local image builds

---

## Step 1 — Push this repository to GitHub

```bash
git init -b main
git add .
git commit -m "feat: BorrowBuddy API"
git remote add origin https://github.com/Harshavardhan-15/borrow-buddy-backend.git
git push -u origin main
```

Before pushing, confirm secrets are ignored:

```bash
git status --ignored
```

`.gitignore` excludes `.env`, `.env.aiven`, `target/`, `uploads/`, logs and IDE
files. `application-dev.properties` intentionally contains **dev-only**
credentials; they are not valid for production.

CI (`.github/workflows/ci.yml`) runs the tests and builds the Docker image on
every push to `main` and on every pull request.

---

## Step 2 — Provision MySQL

Pick one managed provider and note the connection details.

### Managed MySQL (Aiven, Render, …)

1. Create a MySQL 8 instance.
2. Create a database named `borrowbuddy_db` (or use the provider default, for
   example Aiven's `defaultdb`).
3. Create an application user with privileges on that database.
4. Copy the JDBC URL. It should look like:

```
jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
```

5. Allow connections from Render. Most providers expose a public endpoint with
   TLS; if private networking is available, use it.

See [AIVEN_SETUP.md](AIVEN_SETUP.md) for the Aiven-specific walkthrough,
including the SSL requirements and how to migrate an existing database.

### Schema

The API runs with `DB_DDL_AUTO=update` by default, so Hibernate creates the
tables on first boot. For a more controlled setup:

1. Run `database/schema.sql` in MySQL Workbench against the managed database.
2. Set `DB_DDL_AUTO=validate` so the app only verifies the schema.

> **Note:** `database/schema.sql` uses `DATETIME(6)`, `utf8mb4`, and the same
> index/constraint names as the entities. If you change an entity, update the
> script too.

---

## Step 3 — Deploy the API to Render

### Option 3A — Blueprint (`render.yaml`)

The repository includes `render.yaml`. In Render:

1. **New → Blueprint**
2. Connect the GitHub repository.
3. Render reads `render.yaml` and creates the web service.
4. Fill in the secret environment variables it prompts for (`DB_URL`,
   `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `FRONTEND_URL`).
5. Deploy.

### Option 3B — Manual web service

1. **New → Web Service** → connect the repository.
2. Settings:

| Setting | Value |
| --- | --- |
| Runtime | Docker (uses the root `Dockerfile`) **or** Java |
| Root directory | repository root |
| Build command (Java runtime) | `mvn clean package -DskipTests` |
| Start command (Java runtime) | `java -jar target/borrowbuddy-1.0.0.jar` |
| Health check path | `/api/public/stats` |

3. Add the environment variables from the [reference](#environment-variable-reference).
4. Deploy.

### Docker option

The root `Dockerfile` is a multi-stage build:

1. Resolves Maven dependencies, then compiles the jar (tests run in CI).
2. Runs it on a slim JRE as a non-root user.
3. Exposes `8080` and honors `$PORT`.
4. Adds a `HEALTHCHECK` against `/api/public/stats`.

Build and run locally to verify:

```bash
docker build -t borrowbuddy-api .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/borrowbuddy_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e DB_USERNAME=borrowbuddy \
  -e DB_PASSWORD='BorrowBuddy_dev_2026!' \
  -e JWT_SECRET='replace-with-a-long-random-secret-at-least-32-chars' \
  -e FRONTEND_URL='http://localhost:5173' \
  borrowbuddy-api
```

### Notes for Render

- Render sets `PORT` automatically; `application.properties` reads `${PORT:8080}`.
- `server.forward-headers-strategy=framework` makes redirects and client IPs
  correct behind the proxy.
- Free instances sleep after inactivity; the first request after a sleep can
  take 30–60 seconds.
- Keep the Render service and the database in the same region. Each database
  query is a network round trip, and a cross-region database makes list pages
  noticeably slower.
- Uploaded images are stored in MySQL, so the ephemeral filesystem and the free
  plan are fine. The 12 demo photos are imported into the database on first
  start.
- Keep the database small on a free plan: the 5 MB per-image limit and
  `stored_images` rows count against your disk quota. See
  [Image storage](#image-storage).

---

## Step 4 — Connect the frontend

The React app is deployed separately. Set `VITE_API_BASE` in the frontend host
to this service's origin:

```
VITE_API_BASE=https://borrowbuddy-api.onrender.com
```

Then list the frontend origin(s) in this service's CORS configuration:

```
FRONTEND_URL=https://borrowbuddy.vercel.app
```

Multiple origins are comma separated:

```
FRONTEND_URL=https://borrowbuddy.vercel.app,https://borrowbuddy.netlify.app
```

Only exact origins are accepted — wildcards are not. Vercel preview deployments
get a new hostname on every push, so add a preview domain temporarily if you
need to test one, then redeploy this service.

Redeploy the API after changing `FRONTEND_URL`; the value is read at startup.
See the [frontend README](https://github.com/Harshavardhan-15/borrow-buddy-frontend#deploying-to-vercel)
for the Vercel steps.

---

## Step 5 — Verify the deployment

```bash
# Public stats should return JSON
curl https://<backend-host>/api/public/stats

# Register a real account on your domain
curl -X POST https://<backend-host>/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","collegeEmail":"test@college.edu","password":"Password123","confirmPassword":"Password123","studentId":"TEST001"}'

# Login
curl -X POST https://<backend-host>/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"collegeEmail":"test@college.edu","password":"Password123"}'
```

Then in the browser, open the frontend URL and walk the lifecycle: register two
accounts in different browsers/profiles, list an item with one, request it with
the other, approve, open the QR, paste the token in the borrower's scan dialog,
confirm the return and submit a review.

If registration fails with a domain error, update `ALLOWED_EMAIL_DOMAINS` on the
API and restart.

### Verify security headers

```bash
curl -sD - -o /dev/null https://YOUR-SERVICE.onrender.com/api/public/stats
```

Expected (on HTTPS):

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: SAMEORIGIN
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(self), microphone=(), geolocation=(), payment=()
```

### Verify rate limiting

```bash
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST \
    https://YOUR-SERVICE.onrender.com/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"collegeEmail":"nobody@college.edu","password":"wrong"}'
done
```

The first 10 attempts return `400` (bad credentials); the 11th and 12th return
`429` with a `Retry-After` header. The counter resets after 60 seconds.

The limiter keys on the client IP that Spring resolves from `X-Forwarded-For`
(`server.forward-headers-strategy=framework`). This is correct behind a trusted
proxy such as Render, which sets the header. Do not expose the container
directly to the internet, and for multiple instances move the limiter to the
proxy (Cloudflare, Render) or a shared Redis counter.

### Verify CORS

```bash
curl -sD - -o /dev/null -X OPTIONS \
  https://YOUR-SERVICE.onrender.com/api/public/stats \
  -H "Origin: https://your-frontend.vercel.app" \
  -H "Access-Control-Request-Method: GET"
```

`Access-Control-Allow-Origin` must echo the frontend origin exactly.

---

## Docker Compose (self-hosted)

`docker-compose.yml` starts MySQL 8 and the API. It is intended for local
verification or a single VM.

```bash
docker compose up --build
```

- MySQL data is stored in the `mysql-data` volume.
- Images are stored in MySQL (`stored_images`); the `uploads` volume is only a
  fallback for files written by older builds.
- The API waits for MySQL's health check.
- Health endpoint: `http://localhost:8080/api/public/stats`.

Override the defaults with a `.env` file next to `docker-compose.yml`:

```dotenv
MYSQL_ROOT_PASSWORD=change-me
MYSQL_PASSWORD=change-me
JWT_SECRET=replace-with-a-long-random-secret-at-least-32-chars
```

Stop and remove:

```bash
docker compose down          # keep data
docker compose down -v       # delete data and uploads
```

---

## Environment variable reference

### Required in production

| Variable | Example | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates `application-prod.properties` |
| `DB_URL` | `jdbc:mysql://host:3306/borrowbuddy_db?sslMode=REQUIRED&serverTimezone=UTC` | Managed MySQL JDBC URL |
| `DB_USERNAME` | `borrowbuddy` | Database user |
| `DB_PASSWORD` | `••••••••` | Database password |
| `JWT_SECRET` | `openssl rand -base64 48` | 32+ characters, keep secret |
| `FRONTEND_URL` | `https://borrowbuddy.vercel.app` | CORS origin(s) |

### Optional

| Variable | Default | Notes |
| --- | --- | --- |
| `PORT` | `8080` | Render sets it |
| `DB_POOL_SIZE` | `10` | Hikari maximum |
| `DB_DDL_AUTO` | `update` | Use `validate` after first deploy |
| `JWT_EXPIRATION_MS` | `86400000` | 24 hours |
| `ALLOWED_EMAIL_DOMAINS` | `college.edu` | Comma separated, no `@` |
| `UPLOAD_DIR` | `./uploads` | Legacy filesystem fallback; images live in MySQL |
| `UPLOAD_MAX_BYTES` | `5242880` | 5 MB per image |
| `IMPORT_DEMO_IMAGES` | `true` | Copy bundled demo photos into MySQL on startup |
| `RATE_LIMIT_ENABLED` | `true` | Throttle `/api/auth/login` and `/api/auth/register` |
| `RATE_LIMIT_AUTH_PER_MINUTE` | `10` | Attempts per client IP per minute |
| `MAIL_ENABLED` | `false` | In-app notifications still work |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | — | SMTP credentials |
| `REMINDER_CRON` | `0 0 8 * * *` | Daily reminder job |
| `SEED_ENABLED` | `false` | Never enable in production |

The frontend build variable `VITE_API_BASE` belongs to the frontend repository.

Generate a strong secret:

```bash
openssl rand -base64 48
```

Never commit the output. Store it in the hosting dashboard's secret manager.

---

## Image storage

Uploaded images are stored in MySQL, not on disk:

- `ImageStorageService.store()` validates the upload (MIME type, magic bytes,
  5 MB limit) and writes the bytes to the `stored_images` table (`LONGBLOB` plus
  content type, size and SHA-256).
- `GET /uploads/<filename>` (`ImageController`) streams the image with
  `Content-Type`, an `ETag` and
  `Cache-Control: public, max-age=31536000, immutable`. Filenames are UUIDs, so
  repeat views come from the browser cache instead of the database.
- The 12 demo photos are bundled in the jar under `static/uploads/` and copied
  into `stored_images` on startup when missing (`DemoImageImporter`). Set
  `IMPORT_DEMO_IMAGES=false` to skip that.
- Lookup order is database, then `UPLOAD_DIR` on disk, then the classpath. The
  last two keep images written by older builds working; a normal deployment
  needs no disk.

This is what makes Render's free plan viable: no persistent volume, no
object-storage account, and images survive restarts, redeploys and database
restores.

### When to switch to object storage

Each image counts against the database disk quota, grows backups, and sends
every uncached image through the app instance. For a large catalogue, replace
`store()` in `ImageStorageService` with an S3/Cloudinary upload and return the
public URL:

```java
// Pseudocode
String key = "items/" + UUID.randomUUID() + "." + extension;
s3.putObject(bucket, key, file.getInputStream(), metadata);
return "https://cdn.example.com/" + key;
```

Keep the MIME/magic-byte validation and size limit. Item and profile DTOs
already accept `http(s)` URLs, so no frontend change is needed.

### Optional: filesystem storage

To store files instead of rows, mount a persistent disk, point `UPLOAD_DIR` at
it (for example `/var/data/uploads`) and write files there. `ImageController`
still serves them, but an ephemeral host loses them on redeploy.

### Base64 (not recommended)

Storing images as base64 in a text column adds 33% overhead, breaks HTTP caching
and slows list queries.

---

## Scaling and Redis

Two pieces of state are in-memory:

1. **JWT denylist** (`JwtBlacklistService`) — used for logout.
2. **Auth rate limiter** (`RateLimitFilter`) — per-IP counters.
3. **Demo seeding** — disabled in production.

If you run more than one API instance, logout only revokes the token on the
instance that handled the request, and each instance keeps its own rate-limit
counters. Replace both with Redis:

```java
// Pseudocode
redisTemplate.opsForValue().set("jwt:revoked:" + token, "1", ttl);
boolean revoked = Boolean.TRUE.equals(redisTemplate.hasKey("jwt:revoked:" + token));
```

Add `spring-boot-starter-data-redis` and set `SPRING_DATA_REDIS_URL` (or
`spring.data.redis.host/port`). The rest of the app is stateless and scales
horizontally.

---

## Production hardening checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `JWT_SECRET` is random, 32+ characters, stored as a secret
- [ ] Database credentials are secrets, not defaults
- [ ] `DB_DDL_AUTO=validate` after the first deploy
- [ ] `FRONTEND_URL` lists only real frontend origins
- [ ] `ALLOWED_EMAIL_DOMAINS` matches your institution
- [ ] `SEED_ENABLED=false`
- [ ] TLS enforced on the database (`sslMode=REQUIRED` / `useSSL=true`)
- [ ] Image storage sized for expected usage (MySQL `LONGBLOB` or object storage)
- [ ] `MAIL_ENABLED` configured if email is expected
- [ ] Backups enabled on the managed database
- [ ] Logs monitored; no stack traces exposed to clients
- [ ] `RATE_LIMIT_ENABLED=true` (built-in; move to the proxy/Redis for multiple instances)
- [ ] Security headers verified: HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`
- [ ] `server.shutdown=graceful` and compression active (defaults in the `prod` profile)
- [ ] Admin account created deliberately (registration creates `STUDENT` only)
- [ ] Dependency and container image scanning enabled

### Creating an admin

Registration only creates `STUDENT` accounts. Promote one deliberately in MySQL:

```sql
UPDATE students SET role = 'ADMIN' WHERE college_email = 'admin@college.edu';
```

Log out and back in so the new JWT carries the `ADMIN` role.

---

## Database migrations

`ddl-auto=update` is convenient for development but not ideal for production.
Recommended path:

1. First deploy with `DB_DDL_AUTO=update` to create the schema.
2. Export the schema from Workbench (`database/schema.sql` should match).
3. Switch to `DB_DDL_AUTO=validate`.
4. Add Flyway or Liquibase for future changes:

```xml
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>
```

Put migrations in `src/main/resources/db/migration/`.

---

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| App exits on boot: `Could not resolve placeholder 'JWT_SECRET'` | Set `JWT_SECRET` (production profile requires it). |
| `Communications link failure` | Database host/port unreachable, or the provider blocks the connection. Check firewall/private networking. |
| `Access denied for user` | Wrong `DB_USERNAME`/`DB_PASSWORD`, or the user lacks privileges on the database. |
| `Unknown database` | Create `borrowbuddy_db`, or add `createDatabaseIfNotExist=true` to the JDBC URL. |
| Browser CORS error | `FRONTEND_URL` does not match the frontend origin exactly (scheme + host + port). Redeploy after changing it. |
| Login works but API calls return `401` | The frontend is not sending `Authorization: Bearer <token>`; check the frontend's `src/api/client.js` and `VITE_API_BASE`. |
| Images 404 after redeploy | Images are stored in MySQL, so this means the row or database was removed. Restart the app to re-import the demo photos. |
| Render service slow on first request | Free instances sleep. Upgrade or keep the service warm. |
| QR "expired" immediately | Server clock/timezone mismatch; tokens expire at the end of the return date in the server timezone. Keep the DB and app on UTC. |
| `mvn` build fails in Docker | Build from the repository root; the Dockerfile copies `pom.xml` and `src/` from there. |

---

## Go-live runbook

Follow this order the first time you deploy. Steps 1–3 are one-time; later
deploys are just "push to `main`".

1. **Push to GitHub** — see [Step 1](#step-1--push-this-repository-to-github).
   CI builds and tests the API and builds the Docker image.
2. **Provision MySQL** — [Step 2](#step-2--provision-mysql). Keep the JDBC URL,
   user and password; TLS must be required.
3. **Create the Render service** — [Step 3](#step-3--deploy-the-api-to-render).
   Set every required variable, including a fresh `JWT_SECRET`.
4. **Seed demo data once (optional).** The production profile disables seeding.
   For a demo site, temporarily set `SEED_ENABLED=true`, deploy, confirm the
   demo accounts and listings appear, then set it back to `false`. The 12 demo
   photos are imported automatically regardless of `SEED_ENABLED`.
5. **Deploy the frontend** — the
   [frontend repository](https://github.com/Harshavardhan-15/borrow-buddy-frontend)
   has the Vercel steps. Set `VITE_API_BASE` to this service's URL, then put the
   Vercel origin in `FRONTEND_URL` here and redeploy.
6. **Verify** — [Step 5](#step-5--verify-the-deployment): `/api/public/stats`,
   login, browse, image URLs, CORS and the security headers.
7. **Wire automatic deploys (optional).** Add the Render deploy hook as the
   `RENDER_DEPLOY_HOOK_URL` repository secret and, optionally, the public origin
   as the `PUBLIC_BASE_URL` repository variable.
   `.github/workflows/deploy.yml` then triggers a deploy after CI passes on
   `main` and polls the health endpoint. `render.yaml` sets `autoDeploy: true`,
   so set it to `false` if you use the workflow, otherwise each push deploys
   twice.
8. **Harden** — work through the
   [production hardening checklist](#production-hardening-checklist) and switch
   `DB_DDL_AUTO` to `validate`.

---

## Honest deployment status

| Item | Status |
| --- | --- |
| Dockerfile, Compose, Render config | ✅ Written and reviewed |
| Local MySQL run + browser verification | ✅ Completed |
| Aiven for MySQL provisioned + data migrated | ✅ Completed (MySQL 8.4.11) |
| Aiven connection verified from the running app | ✅ Completed |
| Images stored in MySQL and served with caching | ✅ Completed and verified on Aiven |
| Rate limiting + security headers | ✅ Implemented and verified locally |
| Automated tests | ✅ Executed (13 tests, 0 failures) |
| CI (tests + Docker image) | ✅ Workflow written; runs on first push |
| GitHub push | ❌ Not performed from the build environment — no credentials |
| Render backend | ❌ Not deployed |
| Live URL | ❌ Does not exist yet |

The Aiven database is live and the application connects to it. This repository
is deployment-ready; the remaining steps need hosting and Git credentials that
were not available in the build environment.
