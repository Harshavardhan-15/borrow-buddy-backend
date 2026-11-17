# Aiven MySQL setup and migration

BorrowBuddy's development database was migrated from the local MySQL 8.4 instance to an **Aiven for MySQL** service. The application now runs against Aiven through the `prod` Spring profile.

> **Security:** the real host, username and password live only in `.env.aiven`, which is git-ignored. This document uses placeholders. Never commit the real values.

---

## What was migrated

The local `borrowbuddy_db` schema and data were exported with `mysqldump` and imported into the Aiven `defaultdb` database.

| Table | Rows migrated |
| --- | ---: |
| `students` | 7 |
| `items` | 13 |
| `borrow_requests` | 13 |
| `transactions` | 4 |
| `notifications` | 27 |
| `reviews` | 3 |
| `reports` | 0 |

The Aiven server reports **MySQL 8.4.11**. The import used `--ssl-mode=REQUIRED`; Aiven rejects unencrypted connections.

---

## Connection details

From the Aiven console, copy the **Service URI**:

```
mysql://<user>:<password>@<host>:<port>/defaultdb?ssl-mode=REQUIRED
```

Convert it to a JDBC URL:

```
jdbc:mysql://<host>:<port>/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
```

`sslMode=REQUIRED` is the Connector/J equivalent of `ssl-mode=REQUIRED`. If your Aiven plan provides a CA certificate and you want full verification, download `ca.pem` and use `sslMode=VERIFY_CA` with `trustCertificateKeyStoreUrl` / `trustCertificateKeyStorePassword`.

---

## 1. Create the local environment file

Copy the template:

```powershell
Copy-Item aiven.env.example .env.aiven
```

Edit `.env.aiven` in the repository root and fill in:

```properties
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<host>:<port>/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
DB_USERNAME=<user>
DB_PASSWORD=<password>
JWT_SECRET=<64-character random value>
FRONTEND_URL=http://localhost:8080,http://127.0.0.1:8080
```

`.env.aiven` matches the `.env.*` rule in `.gitignore`, so Git will not track it.

---

## 2. Run against Aiven

Make sure Java 21 and Maven are available, then, from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-aiven.ps1
```

If Maven is not on `PATH`, point the script at it:

```powershell
$env:MAVEN_CMD="C:\path\to\apache-maven-3.9.9\bin\mvn.cmd"
powershell -ExecutionPolicy Bypass -File scripts\run-aiven.ps1
```

Or start it manually:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:mysql://<host>:<port>/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8"
$env:DB_USERNAME="<user>"
$env:DB_PASSWORD="<password>"
$env:JWT_SECRET="<64-character random value>"
$env:FRONTEND_URL="http://localhost:5173"
mvn spring-boot:run
```

Open `http://localhost:8080/api/public/stats` to confirm the API is up. Run the
React app from the frontend repository (`npm run dev`) to use the UI; its Vite
proxy forwards `/api` and `/uploads` to port 8080. Sign in with a seeded account
such as `aarav@college.edu` / `Password123`.

---

## 3. Verify the connection

### From MySQL Workbench

Create a new connection:

| Field | Value |
| --- | --- |
| Hostname | your Aiven host |
| Port | your Aiven port |
| Username | your Aiven user |
| Password | your Aiven password |
| Default schema | `defaultdb` |
| SSL | Require |

Then run:

```sql
SELECT COUNT(*) FROM students;
SELECT id, item_name, price_per_day, security_deposit FROM items ORDER BY id;
```

### From the API

```powershell
Invoke-RestMethod http://localhost:8080/api/public/stats
Invoke-RestMethod http://localhost:8080/api/items?size=3
```

---

## Re-running the migration

If you need to copy the local database again, use the MySQL command-line tools.

```powershell
# 1. Dump the local database (no CREATE DATABASE / USE statements)
$env:MYSQL_PWD="<local-root-password>"
& "C:\Program Files\MySQL\MySQL Workbench 8.0 CE\mysqldump.exe" `
  --protocol=TCP -h 127.0.0.1 -P 3306 -u root `
  --default-character-set=utf8mb4 --single-transaction `
  --set-gtid-purged=OFF --no-tablespaces --routines --triggers `
  --column-statistics=0 --skip-add-locks borrowbuddy_db `
  --result-file="C:\temp\borrowbuddy-local.sql"
Remove-Item Env:MYSQL_PWD

# 2. Import into Aiven defaultdb
$env:MYSQL_PWD="<aiven-password>"
& "C:\Program Files\MySQL\MySQL Workbench 8.0 CE\mysql.exe" `
  --protocol=TCP -h <host> -P <port> -u <user> --ssl-mode=REQUIRED `
  --default-character-set=utf8mb4 defaultdb `
  -e "source C:/temp/borrowbuddy-local.sql"
Remove-Item Env:MYSQL_PWD
```

The dump deliberately omits `CREATE DATABASE` and `USE`, because Aiven gives you the `defaultdb` database and the application user may not be allowed to create databases.

---

## Deploying the backend with Aiven

Set these environment variables on Render (or any Java host):

```
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<host>:<port>/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=utf8
DB_USERNAME=<user>
DB_PASSWORD=<password>
JWT_SECRET=<64-character random value>
FRONTEND_URL=https://<your-frontend-domain>
DB_DDL_AUTO=validate
```

Run the schema migration before switching `DB_DDL_AUTO` to `validate` in production. Back up the Aiven database before applying schema changes.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `SSL Connection required` | Add `sslMode=REQUIRED` to the JDBC URL. |
| `Unknown database 'borrowbuddy_db'` | Aiven uses `defaultdb`; update `DB_URL`. |
| `Access denied` | Check the Aiven user/password and that the service is running. |
| `Communications link failure` | Aiven may have an IP allow-list, or the service is powered off. |
| `Public Key Retrieval is not allowed` | Keep `sslMode=REQUIRED`; do not disable TLS. |
| Schema changes not applied | Set `DB_DDL_AUTO=update` once, then switch to `validate`. |

---

## Rotating the password

1. Reset the password in the Aiven console.
2. Update `.env.aiven`.
3. Restart the backend.
4. If the old password was shared in chat or committed anywhere, treat it as compromised and rotate it.
