# Testing BorrowBuddy

This document describes the automated tests, the manual browser checks performed
during development, and how to reproduce them.

---

## Automated tests

### Run

From the repository root:

```powershell
mvn test
```

or a full package build:

```powershell
mvn clean package
```

### Configuration

`src/test/resources/application-test.properties`:

- H2 in-memory database in MySQL compatibility mode (`MODE=MySQL`)
- `ddl-auto=create-drop`
- Seeding disabled (`app.seed.enabled=false`)
- Mail disabled
- Test-only JWT secret
- Uploads written to `target/test-uploads`
- Demo image import disabled and rate limiting disabled (`RateLimitTest`
  re-enables it with its own limit)

No MySQL server or external service is required.

### Coverage

#### `BorrowFlowIntegrationTest`

A full-stack MockMvc test that exercises the core lifecycle:

1. Register two students and capture their JWTs.
2. Create an item as the owner.
3. Reject a self-borrow attempt.
4. Create a borrow request as the borrower.
5. Approve the request as the owner.
6. Assert a transaction is created in `AWAITING_HANDOVER`.
7. Generate the owner's QR and capture the raw token.
8. Scan/paste the token as the borrower and assert the handover is confirmed.
9. Assert the QR token cannot be reused.
10. Confirm the return.
11. Assert `canReview` becomes true.
12. Submit reviews from both participants.
13. Assert a duplicate review is rejected with `409`.
14. Assert the public review summary reports the correct count.

#### `QrCodeServiceTest`

Unit tests for QR generation and token handling:

- QR image is produced as a PNG data URL.
- Tokens are random and non-empty.
- SHA-256 hashing is deterministic.
- `matches()` accepts the correct raw token and rejects a different one.

#### `StoredImageTest`

Database-backed image storage and serving:

- Uploads a JPEG, reads it back byte-for-byte from `/uploads/<name>`.
- Asserts the `ETag` and immutable `Cache-Control` header, and a conditional
  request returns `304`.
- Serves a bundled demo photo when the database has no copy.
- Returns `404` for unknown names and rejects path traversal.
- Rejects non-images (magic bytes) and disallowed content types.

#### `RateLimitTest`

- Login returns `429` with `Retry-After` after the configured number of attempts.
- Registration is throttled the same way.
- Public endpoints are not affected.

### Last recorded run

```
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Re-run `mvn test` to confirm on your machine. Do not treat this document as a
substitute for an actual run.

### Suggested additions

- Repository tests for overlap queries and soft-delete counts.
- Security tests for every `403` path (non-owner edit/delete, non-participant
  QR/return).
- Upload test for an oversized file (the 5 MB limit).
- Scheduler test for reminder/overdue notification creation.
- Frontend smoke test (Playwright) for the 12 pages, in the frontend repository.

---

## Manual browser verification

Performed against the running application with MySQL-backed demo data and fresh
QA accounts. The React build was served by the API at `http://localhost:8080`,
and a separately hosted build (Vite preview + cross-origin API) was verified the
same way. Every page returned HTTP 200 and produced no console errors.

### Pages and layout

| Page | Verified |
| --- | --- |
| `/` | Public stats, featured items, navigation, hero |
| `/login` | Demo autofill, validation, redirect to dashboard |
| `/register` | Field validation, domain restriction, redirect |
| `/dashboard` | Stats, upcoming returns, scan modal binding |
| `/browse` | Search, category chips, empty state, pagination summary |
| `/items/:id` | Details, availability, borrow form, owner actions, contact phone visibility |
| `/items/new` | Required-field validation, image URL handling |
| `/requests` | Sent/received tabs, approve/reject/cancel |
| `/notifications` | List, unread count, mark-all-read, badge clear |
| `/history` | Transaction cards, status pills, QR/scan/return actions |
| `/profile` and `/students/:id` | Trust score, listing cards, edit profile |
| `/reviews` | Pending/given tabs, star input, submit, empty states |

### End-to-end flow (fresh QA accounts)

1. Registered an owner and a borrower through the UI.
2. Owner created an item.
3. Borrower submitted a dated request.
4. Owner approved it.
5. Owner opened the QR modal — verified a PNG data URL, raw token and expiry.
6. Borrower opened the scan modal, pasted the token, confirmed handover —
   verified the toast and `BORROWED` status.
7. Borrower confirmed the return — verified `COMPLETED` and a "Leave a review"
   action.
8. Reviews page showed the pending review — submitted 5 stars and a comment.
9. Given-review tab listed the new review; the pending tab showed the empty
   state.

### Additional checks

- **Search:** `?q=calculator` returned 2 matching items; the Books category chip
  returned an empty state.
- **Notifications:** mark-all-read cleared the nav badge and updated the unread
  counter to 0.
- **Cancel request:** confirmation modal, toast, and `Cancelled` pill all
  rendered.
- **Add item:** submitting an empty form produced four inline validation
  messages.
- **Profile listings:** after changing `StudentProfileDto.items` to `ItemDto`,
  4 full item cards render.
- **Images:** all 12 demo photos loaded; uploaded JPEGs were byte-identical on
  read-back, returned an `ETag`, and `304` on conditional requests.
- **Split hosting:** with the UI on a different origin, login, CORS preflight
  and every image URL resolved against the API origin.

### Reproduce locally

```powershell
# 1. Start MySQL, then the API from the repository root
mvn spring-boot:run

# 2. Start the UI from the frontend repository
npm run dev

# 3. Open the app
start http://localhost:5173/
```

For a clean lifecycle, register two new `@college.edu` accounts (registration is
open) or reset the database.

---

## API smoke test with PowerShell

```powershell
$login = Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/api/auth/login' `
  -ContentType 'application/json' `
  -Body '{"collegeEmail":"aarav@college.edu","password":"Password123"}'

$headers = @{ Authorization = "Bearer " + $login.data.token }

Invoke-RestMethod -Uri 'http://localhost:8080/api/dashboard/stats' -Headers $headers
Invoke-RestMethod -Uri 'http://localhost:8080/api/items/mine' -Headers $headers
Invoke-RestMethod -Uri 'http://localhost:8080/api/reviews/pending' -Headers $headers
```

Or import `postman/BorrowBuddy.postman_collection.json` and run the **Auth →
Login** request first; the collection stores the token automatically.

---

## What is not covered

- No load or performance testing.
- No automated frontend tests yet.
- No automated email delivery test (mail is optional and disabled by default).
- The scheduler's cron job is not covered by an integration test; verify it by
  temporarily setting a frequent cron and watching notifications.
- Moderation endpoints require an `ADMIN` role; promote a user in MySQL to test
  the UI/API manually.
