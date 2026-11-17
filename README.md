# BorrowBuddy — backend API

The BorrowBuddy API: a Spring Boot 3 / Java 21 service that powers a campus
borrow-and-lend platform. Students list items they own, request items from other
students, hand items over with a QR code, and review each other afterwards.

The React UI lives in
[borrow-buddy-frontend](https://github.com/Harshavardhan-15/borrow-buddy-frontend).

## Live deployment

| Piece | URL |
| --- | --- |
| API | <https://borrowbuddy-api-zeu2.onrender.com> |
| Health check | <https://borrowbuddy-api-zeu2.onrender.com/api/public/stats> |
| React app (Vercel) | <https://borrowbuddy-lyart.vercel.app> |

The API runs on Render (Frankfurt) against the Aiven MySQL service. Free Render
instances sleep after inactivity, so the first request can take 30–60 seconds.

## Features

- **JWT auth** with a college-email domain allow-list, BCrypt hashing and a
  logout denylist.
- **Items** with categories, pricing, deposits, availability windows, search and
  pagination.
- **Borrow requests** with approval, rejection and cancellation, plus overlap
  detection so one item cannot be double-booked.
- **QR handover** — the owner generates a short-lived token, the borrower scans
  or pastes it, and the transaction moves to `BORROWED`.
- **Returns, history and reviews** with a 1–5 star rating, duplicate protection
  and a public review summary.
- **Dashboards and notifications** for upcoming returns, overdue items and
  request updates, with an optional daily reminder job.
- **Images in MySQL** (`stored_images` `LONGBLOB`) served with `ETag` and
  immutable caching, so no persistent disk is needed.
- **Production hardening** — security headers, gzip compression, graceful
  shutdown, auth rate limiting, exact-origin CORS and validated uploads.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3 (Web, Security, Data JPA, Validation, Mail) |
| Database | MySQL 8 (Aiven in production, MySQL Workbench locally); H2 for tests |
| Auth | JJWT 0.12, BCrypt |
| QR | ZXing |
| Build | Maven |
| Deploy | Docker on Render, plus Docker Compose for self-hosting |
| CI | GitHub Actions (tests + Docker image) |

## Requirements

- JDK 21
- Maven 3.9+
- MySQL 8 (local install or a managed instance)

## Local development

1. Create the database and user in MySQL Workbench:

   ```sql
   CREATE DATABASE borrowbuddy_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'borrowbuddy'@'%' IDENTIFIED BY 'change-me';
   GRANT ALL PRIVILEGES ON borrowbuddy_db.* TO 'borrowbuddy'@'%';
   ```

2. Set the environment variables (PowerShell):

   ```powershell
   $env:SPRING_PROFILES_ACTIVE="prod"
   $env:DB_URL="jdbc:mysql://127.0.0.1:3306/borrowbuddy_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
   $env:DB_USERNAME="borrowbuddy"
   $env:DB_PASSWORD="change-me"
   $env:JWT_SECRET="replace-with-a-long-random-secret-at-least-32-chars"
   $env:FRONTEND_URL="http://localhost:5173"
   ```

   See `.env.example` for every option. Never commit a filled-in file.

3. Run the API:

   ```powershell
   mvn spring-boot:run
   ```

4. Check it is up:

   ```powershell
   Invoke-RestMethod http://localhost:8080/api/public/stats
   ```

Hibernate creates the schema on first boot (`DB_DDL_AUTO=update`). To create it
explicitly, run `database/schema.sql` in MySQL Workbench and set
`DB_DDL_AUTO=validate`.

### Demo data

With `SEED_ENABLED=true` (or the `dev` profile) the app seeds demo accounts and
listings. All demo accounts use the password `Password123`:

`aarav@college.edu`, `priya@college.edu`, `rohan@college.edu`,
`sara@college.edu`.

The 12 demo photos are bundled in the jar and imported into `stored_images` on
startup regardless of `SEED_ENABLED`; set `IMPORT_DEMO_IMAGES=false` to skip it.

## Running against Aiven MySQL

Aiven requires TLS. Copy `aiven.env.example` to `.env.aiven`, fill in the
connection values, then:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-aiven.ps1
```

Full walkthrough, including how the existing database was migrated:
[docs/AIVEN_SETUP.md](docs/AIVEN_SETUP.md).

## Tests

```powershell
mvn test
```

The suite runs on H2 in MySQL compatibility mode, so no database is needed:

- `BorrowFlowIntegrationTest` — the full borrow → QR handover → return → review
  lifecycle.
- `QrCodeServiceTest` — token generation, hashing and matching.
- `StoredImageTest` — upload/read-back, `ETag`/`304`, demo fallback, traversal
  and content-type rejection.
- `RateLimitTest` — login and registration throttling.

Details: [docs/TESTING.md](docs/TESTING.md).

## API

Base path: `/api`. Responses use the envelope
`{ "success", "message", "data", "timestamp" }`; validation errors are listed in
`data.fieldErrors`. Authenticated calls send `Authorization: Bearer <token>`.

| Area | Endpoints |
| --- | --- |
| Public | `GET /api/public/stats`, `GET /api/public/reviews/summary` |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout` |
| Items | `GET /api/items`, `GET /api/items/{id}`, `GET /api/items/mine`, `POST/PUT/DELETE /api/items...` |
| Requests | `GET /api/requests/sent`, `/received`, `POST /api/requests`, approve/reject/cancel |
| Transactions | `GET /api/transactions`, QR generation, handover scan, return confirmation |
| Reviews | `GET /api/reviews/pending`, `/given`, `POST /api/reviews` |
| Dashboard | `GET /api/dashboard/stats` |
| Profile | `GET/PUT /api/students/profile`, `GET /api/students/{id}` |
| Notifications | `GET /api/notifications`, unread count, mark read |
| Images | `GET /uploads/{filename}` |
| Admin | Report and moderation endpoints (role `ADMIN`) |

Full request/response reference: [docs/API.md](docs/API.md). A Postman
collection is in [`postman/`](postman/BorrowBuddy.postman_collection.json).

## Docker

```bash
docker build -t borrowbuddy-api .
docker run --rm -p 8080:8080 --env-file .env borrowbuddy-api
```

`docker compose up --build` starts MySQL 8 and the API together. See
[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#docker-compose-self-hosted).

## Deployment

`render.yaml` is a Render blueprint for the API container. The React app is
deployed separately on Vercel and points at this service with `VITE_API_BASE`;
this service allows that origin through `FRONTEND_URL`.

Step-by-step runbook, environment reference, image storage notes and the
hardening checklist: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Project structure

```
├── Dockerfile                  # Multi-stage API image
├── docker-compose.yml          # MySQL + API for local/self-hosted runs
├── render.yaml                 # Render blueprint
├── database/schema.sql         # Full MySQL schema
├── docs/                       # API, deployment, testing, Aiven, credits
├── postman/                    # Postman collection
├── scripts/run-aiven.ps1       # Start against Aiven MySQL
└── src/
    ├── main/java/com/borrowbuddy/
    │   ├── config/             # Security, CORS, filters, scheduler
    │   ├── controller/         # REST endpoints
    │   ├── dto/                # Request/response records
    │   ├── entity/             # JPA entities
    │   ├── repository/         # Spring Data repositories
    │   └── service/            # Business logic, QR, images, mail
    └── main/resources/
        ├── application*.properties
        └── static/uploads/     # Bundled demo photos
```

## Configuration

| Variable | Default | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` requires `JWT_SECRET` |
| `PORT` | `8080` | Render sets it |
| `DB_URL` | local MySQL | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | — | Database credentials |
| `DB_POOL_SIZE` | `10` | Hikari maximum |
| `DB_DDL_AUTO` | `update` | Use `validate` after the first deploy |
| `JWT_SECRET` | — | 32+ characters, required in `prod` |
| `JWT_EXPIRATION_MS` | `86400000` | 24 hours |
| `FRONTEND_URL` | `http://localhost:5173` | CORS origins, comma separated |
| `ALLOWED_EMAIL_DOMAINS` | `college.edu` | Registration domain allow-list |
| `UPLOAD_MAX_BYTES` | `5242880` | 5 MB per image |
| `IMPORT_DEMO_IMAGES` | `true` | Import bundled photos into MySQL |
| `RATE_LIMIT_ENABLED` | `true` | Throttle login/registration |
| `RATE_LIMIT_AUTH_PER_MINUTE` | `10` | Attempts per client IP per minute |
| `MAIL_ENABLED` | `false` | In-app notifications still work |
| `REMINDER_CRON` | `0 0 8 * * *` | Daily reminder job |
| `SEED_ENABLED` | `false` | Never enable in production |

## License and credits

Demo photos are from Wikimedia Commons; authors and licences are listed in
[docs/IMAGE_CREDITS.md](docs/IMAGE_CREDITS.md).
