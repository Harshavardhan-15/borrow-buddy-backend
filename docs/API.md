# BorrowBuddy API reference

Base URL (local): `http://localhost:8080`

All endpoints return a JSON envelope:

```json
{
  "success": true,
  "message": "OK",
  "data": {},
  "timestamp": "2026-11-05T18:00:00Z"
}
```

Authentication uses a bearer token:

```
Authorization: Bearer <jwt>
```

Protected endpoints return `401` when the token is missing/expired/revoked and `403` when the caller lacks permission.

---

## Contents

- [Authentication](#authentication)
- [Items](#items)
- [Borrow requests](#borrow-requests)
- [Transactions and QR handover](#transactions-and-qr-handover)
- [Reviews](#reviews)
- [Students and profiles](#students-and-profiles)
- [Notifications](#notifications)
- [Dashboard](#dashboard)
- [Public](#public)
- [Uploads](#uploads)
- [Reports and moderation](#reports-and-moderation)
- [Errors](#errors)
- [Enums](#enums)

---

## Authentication

### `POST /api/auth/register`

Public. Creates a student account.

Request:

```json
{
  "name": "Aarav Sharma",
  "collegeEmail": "aarav@college.edu",
  "password": "Password123",
  "confirmPassword": "Password123",
  "studentId": "STU2024001"
}
```

Rules: email must belong to `ALLOWED_EMAIL_DOMAINS`; password 8–72 characters with at least one letter and one number; student ID may contain letters, numbers, `.`, `-`, `_`.

Response `201`:

```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "tokenType": "Bearer",
    "expiresInMs": 86400000,
    "user": {
      "id": 1,
      "name": "Aarav Sharma",
      "collegeEmail": "aarav@college.edu",
      "studentId": "STU2024001",
      "role": "STUDENT",
      "profileImage": null,
      "averageRating": 0.0,
      "reviewCount": 0,
      "createdAt": "2026-11-05T17:46:16Z"
    }
  },
  "timestamp": "2026-11-05T18:00:00Z"
}
```

### `POST /api/auth/login`

Public.

```json
{ "collegeEmail": "aarav@college.edu", "password": "Password123" }
```

Returns the same `AuthResponse` shape as registration.

### `POST /api/auth/logout`

Authenticated. Revokes the current token until its natural expiry.

```json
{ "success": true, "message": "Signed out successfully", "data": null, "timestamp": "..." }
```

### `GET /api/auth/me`

Authenticated. Returns the current `UserDto`.

---

## Items

### `GET /api/items`

Public. Browse/search with pagination.

| Query | Type | Default | Notes |
| --- | --- | --- | --- |
| `q` | string | — | Matches item name, description, category |
| `category` | string | — | Exact category |
| `condition` | enum | — | `NEW`, `LIKE_NEW`, `GOOD`, `FAIR`, `POOR` |
| `availableOnly` | boolean | `false` | Exclude reserved/unavailable items |
| `page` | int | `0` | Zero-based |
| `size` | int | `12` | Max 48 |
| `sort` | string | `createdAt` | `createdAt`, `itemName`, `category`, `price` |
| `dir` | string | `desc` | `asc` or `desc` |

Response `data`:

```json
{
  "content": [
    {
      "id": 1,
      "itemName": "Casio FX-991EX Scientific Calculator",
      "category": "Electronics",
      "description": "Exam-approved scientific calculator...",
      "imageUrl": null,
      "itemCondition": "GOOD",
      "availabilityStatus": "AVAILABLE",
      "pricePerDay": 20.00,
      "securityDeposit": 200.00,
      "contactPhone": "+91 98765 43210",
      "availableNow": true,
      "reservedNow": false,
      "nextAvailableDate": null,
      "owner": {
        "id": 1,
        "name": "Aarav Sharma",
        "profileImage": null,
        "averageRating": 4.8,
        "reviewCount": 5
      },
      "createdAt": "2026-11-05T17:46:16Z",
      "ownedByMe": false
    }
  ],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

### `GET /api/items/search`

Public. Same contract as `GET /api/items` with `q` required.

### `GET /api/items/featured`

Public. Up to 6 available items, newest first. Returns `ItemDto[]`.

### `GET /api/items/mine`

Authenticated. All non-deleted items owned by the caller. Returns `ItemDto[]`.

### `GET /api/items/{id}`

Public (an authenticated owner/admin can still see a soft-deleted item). Returns one `ItemDto`.

### `POST /api/items`

Authenticated. Creates a listing.

```json
{
  "itemName": "Casio FX-991EX Scientific Calculator",
  "category": "Electronics",
  "description": "Exam-approved scientific calculator with 552 functions.",
  "imageUrl": "/uploads/8f2c....png",
  "itemCondition": "GOOD",
  "availabilityStatus": "AVAILABLE",
  "pricePerDay": 20.00,
  "securityDeposit": 200.00,
  "contactPhone": "+91 98765 43210"
}
```

`imageUrl` is optional and must be an `/uploads/...` path or an `http(s)` URL. `pricePerDay` and `securityDeposit` are optional non-negative amounts in INR (max 2 decimal places); omitting them or sending `0` means free / no deposit. `contactPhone` is optional (max 20 characters; digits, spaces, `+`, `-` and parentheses). It is returned only to authenticated callers, so anonymous `GET /api/items/{id}` responses have `contactPhone: null`. Returns `201` with the created `ItemDto`.

### `PUT /api/items/{id}`

Authenticated, owner or admin only. Same body as create. Returns the updated `ItemDto`.

### `DELETE /api/items/{id}`

Authenticated, owner or admin only. Soft-deletes the listing.

- `409` if the item has pending/approved requests or an active transaction.
- `403` if the caller is not the owner.
- `404` if already deleted.

```json
{ "success": true, "message": "Item removed", "data": null, "timestamp": "..." }
```

---

## Borrow requests

### `POST /api/borrow-requests`

Authenticated. Creates a pending request.

```json
{
  "itemId": 1,
  "startDate": "2026-11-06",
  "returnDate": "2026-11-08",
  "message": "Need it for the CS201 exam."
}
```

Rules: no self-borrow, item must be available, start date not in the past, return date after start, maximum 90 days, no duplicate active request for the same item, and no overlap with an approved reservation. Returns `201`.

### `GET /api/borrow-requests/sent`

Authenticated. Requests created by the caller, newest first.

### `GET /api/borrow-requests/received`

Authenticated. Requests for items owned by the caller, newest first.

### `GET /api/borrow-requests/{id}`

Authenticated. Borrower, owner or admin only.

### `PUT /api/borrow-requests/{id}/approve`

Owner or admin only. Locks the request and item rows, re-checks overlaps, sets the request to `APPROVED`, creates an `AWAITING_HANDOVER` transaction, notifies the borrower, and auto-rejects conflicting pending requests.

### `PUT /api/borrow-requests/{id}/reject`

Owner or admin only. Only `PENDING` requests can be rejected. Notifies the borrower.

### `PUT /api/borrow-requests/{id}/cancel`

Borrower only. Only `PENDING` requests can be cancelled. Notifies the owner.

`BorrowRequestDto`:

```json
{
  "id": 4,
  "item": {
    "id": 1,
    "itemName": "Casio FX-991EX Scientific Calculator",
    "category": "Electronics",
    "imageUrl": null,
    "itemCondition": "GOOD",
    "availabilityStatus": "AVAILABLE",
    "pricePerDay": 20.00,
    "securityDeposit": 200.00,
    "ownerId": 1,
    "ownerName": "Aarav Sharma"
  },
  "borrower": { "id": 2, "name": "Priya Nair", "profileImage": null, "averageRating": 4.9, "reviewCount": 7 },
  "owner": { "id": 1, "name": "Aarav Sharma", "profileImage": null, "averageRating": 4.8, "reviewCount": 5 },
  "requestDate": "2026-11-05",
  "startDate": "2026-11-06",
  "returnDate": "2026-11-08",
  "message": "Need it for the CS201 exam.",
  "status": "APPROVED",
  "createdAt": "2026-11-05T18:00:00Z",
  "transactionId": 3,
  "transactionStatus": "AWAITING_HANDOVER"
}
```

---

## Transactions and QR handover

A transaction is created automatically when a request is approved.

`TransactionDto`:

```json
{
  "id": 3,
  "requestId": 4,
  "item": {
    "id": 1,
    "itemName": "Casio FX-991EX Scientific Calculator",
    "category": "Electronics",
    "imageUrl": null,
    "itemCondition": "GOOD",
    "availabilityStatus": "AVAILABLE",
    "pricePerDay": 20.00,
    "securityDeposit": 200.00,
    "ownerId": 1,
    "ownerName": "Aarav Sharma"
  },
  "borrower": { "id": 2, "name": "Priya Nair", "profileImage": null, "averageRating": 4.9, "reviewCount": 7 },
  "owner": { "id": 1, "name": "Aarav Sharma", "profileImage": null, "averageRating": 4.8, "reviewCount": 5 },
  "startDate": "2026-11-06",
  "returnDate": "2026-11-08",
  "handoverDate": null,
  "actualReturnDate": null,
  "status": "AWAITING_HANDOVER",
  "overdue": false,
  "qrAvailable": true,
  "canConfirmHandover": false,
  "canConfirmReturn": false,
  "canReview": false,
  "reviewId": null,
  "createdAt": "2026-11-05T18:00:00Z"
}
```

### `GET /api/transactions`

Authenticated. All transactions where the caller is the borrower or owner.

### `GET /api/transactions/{id}`

Authenticated. Participant or admin only.

### `GET /api/transactions/{id}/qr`

Owner only. Generates a fresh QR token for an `AWAITING_HANDOVER` transaction. **Each call rotates the token**, invalidating the previous one. Only the SHA-256 hash is stored. The token expires at the end of the return date.

Response:

```json
{
  "token": "P6XLK44SjS9odsevKH65xGesCnPI8q4o4u5MoRcd6p8",
  "expiresAt": "2026-11-09T18:29:59Z",
  "imageDataUrl": "data:image/png;base64,iVBORw0KGgo..."
}
```

### `PUT /api/transactions/{id}/handover`

Authenticated participant. Alternative to scanning; accepts the token directly.

```json
{ "token": "P6XLK44SjS9odsevKH65xGesCnPI8q4o4u5MoRcd6p8" }
```

### `POST /api/transactions/scan`

Authenticated. Used by the borrower's scan/paste flow. The server finds the transaction by token hash and confirms the handover.

```json
{ "token": "P6XLK44SjS9odsevKH65xGesCnPI8q4o4u5MoRcd6p8" }
```

Rules: token must match the stored hash, not be expired, and the caller must be the borrower. Marks the transaction `BORROWED`, records `handoverDate`, and notifies the owner. Returns the updated `TransactionDto`.

### `PUT /api/transactions/{id}/return`

Authenticated participant. Marks the transaction `COMPLETED`, records `actualReturnDate`, completes the request, frees the item, and notifies both parties.

---

## Reviews

### `POST /api/reviews`

Authenticated. Only participants of a `COMPLETED` transaction, once per reviewer.

```json
{
  "transactionId": 3,
  "rating": 5,
  "comment": "Great item, smooth handover."
}
```

Returns `201` with a `ReviewDto`. Returns `409` for duplicates or ineligible transactions.

### `GET /api/reviews/pending`

Authenticated. Completed transactions where the caller has not yet reviewed the other participant. Returns `TransactionDto[]` with `canReview: true`.

### `GET /api/reviews/mine`

Authenticated. Reviews written by the caller, newest first. Returns `ReviewDto[]`.

### `GET /api/reviews/{studentId}`

Public. Reviews received by a student plus the aggregate.

```json
{
  "averageRating": 4.8,
  "reviewCount": 5,
  "reviews": [
    {
      "id": 9,
      "reviewer": { "id": 2, "name": "Priya Nair", "profileImage": null, "averageRating": 4.9, "reviewCount": 7 },
      "reviewedStudent": { "id": 1, "name": "Aarav Sharma", "profileImage": null, "averageRating": 4.8, "reviewCount": 5 },
      "transactionId": 3,
      "itemName": "Casio FX-991EX Scientific Calculator",
      "rating": 5,
      "comment": "Great item, smooth handover.",
      "createdAt": "2026-11-05T18:10:00Z"
    }
  ]
}
```

---

## Students and profiles

### `GET /api/students/profile`

Authenticated. Current student's profile.

### `PUT /api/students/profile`

Authenticated.

```json
{
  "name": "Aarav Sharma",
  "studentId": "STU2024001",
  "profileImage": "/uploads/avatar.png"
}
```

Returns the updated `StudentProfileDto`.

### `GET /api/students/{id}`

Authenticated. Profile and listings for another student (used by profile links).

`StudentProfileDto`:

```json
{
  "user": {
    "id": 1,
    "name": "Aarav Sharma",
    "collegeEmail": "aarav@college.edu",
    "studentId": "STU2024001",
    "role": "STUDENT",
    "profileImage": null,
    "averageRating": 4.8,
    "reviewCount": 5,
    "createdAt": "2026-11-05T17:46:16Z"
  },
  "listedItems": 4,
  "totalBorrowed": 2,
  "totalLent": 6,
  "completedBorrows": 2,
  "completedLends": 5,
  "averageRating": 4.8,
  "reviewCount": 5,
  "items": [ { "id": 1, "itemName": "...", "availableNow": true, "owner": { } } ]
}
```

`items` is a list of full `ItemDto` objects so the shared item card can render them.

---

## Notifications

### `GET /api/notifications?page=0&size=20`

Authenticated. Paginated `NotificationDto` list, newest first.

```json
{
  "id": 12,
  "message": "Priya Nair requested to borrow \"Casio FX-991EX\" from 2026-11-06 to 2026-11-08.",
  "type": "BORROW_REQUEST",
  "read": false,
  "referenceId": 4,
  "link": "borrow-requests.html",
  "createdAt": "2026-11-05T18:00:00Z"
}
```

### `GET /api/notifications/unread-count`

Authenticated.

```json
{ "count": 3 }
```

### `PUT /api/notifications/{id}/read`

Authenticated. Marks one notification as read and returns it.

### `PUT /api/notifications/read-all`

Authenticated. Marks every unread notification as read.

```json
{ "updated": 3 }
```

---

## Dashboard

### `GET /api/dashboard/stats`

Authenticated.

```json
{
  "listedItems": 4,
  "activeBorrowRequests": 0,
  "pendingLendingRequests": 1,
  "currentlyBorrowed": 0,
  "completedBorrows": 0,
  "completedLends": 0,
  "unreadNotifications": 2,
  "averageRating": 0.0,
  "upcomingReturns": [ { "id": 3, "status": "BORROWED", "returnDate": "2026-11-10" } ],
  "recentActivity": [ { "id": 12, "type": "BORROW_REQUEST", "read": false } ]
}
```

`upcomingReturns` is a `TransactionDto[]`; `recentActivity` is a `NotificationDto[]`.

---

## Public

### `GET /api/public/stats`

Public. Used by the landing page.

```json
{ "students": 6, "items": 11, "completedBorrows": 3 }
```

---

## Uploads

### `POST /api/uploads`

Authenticated. `multipart/form-data` with a single `file` field.

Allowed types: JPEG, PNG, WEBP, GIF (MIME type + magic-byte check). Maximum 5 MB.

```json
{ "url": "/uploads/3f1c9b0e-....png" }
```

Use the returned `url` as `imageUrl` when creating/updating an item or profile.

---

## Reports and moderation

### `POST /api/reports`

Authenticated. Report a user, review or item.

```json
{
  "targetType": "REVIEW",
  "targetId": 9,
  "reason": "Abusive language in the comment."
}
```

### `GET /api/admin/reports?status=OPEN`

`ROLE_ADMIN` only. Lists reports, optionally filtered by `OPEN`, `RESOLVED`, `DISMISSED`.

### `PUT /api/admin/reports/{id}?status=RESOLVED`

`ROLE_ADMIN` only. Updates a report's status.

---

## Errors

| Status | Meaning |
| --- | --- |
| `400` | Validation failure or invalid business input |
| `401` | Missing, invalid, expired or revoked token |
| `403` | Authenticated but not allowed (not owner/participant/admin) |
| `404` | Resource not found (also used for hidden soft-deleted items) |
| `409` | Conflict — overlap, duplicate review, active requests, expired QR, wrong state |
| `413` | Upload too large |
| `500` | Unexpected server error (details are not exposed) |

Validation error body:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "fieldErrors": {
      "password": "Password must contain at least one letter and one number"
    }
  },
  "timestamp": "2026-11-05T18:00:00Z"
}
```

Business error body:

```json
{
  "success": false,
  "message": "Those dates are already reserved. Please choose different dates.",
  "data": null,
  "timestamp": "2026-11-05T18:00:00Z"
}
```

---

## Enums

| Enum | Values |
| --- | --- |
| `Role` | `STUDENT`, `ADMIN` |
| `AvailabilityStatus` | `AVAILABLE`, `UNAVAILABLE` |
| `ItemCondition` | `NEW`, `LIKE_NEW`, `GOOD`, `FAIR`, `POOR` |
| `RequestStatus` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED` |
| `TransactionStatus` | `AWAITING_HANDOVER`, `BORROWED`, `OVERDUE`, `COMPLETED` |
| `NotificationType` | `BORROW_REQUEST`, `REQUEST_APPROVED`, `REQUEST_REJECTED`, `REQUEST_CANCELLED`, `RETURN_REMINDER`, `OVERDUE`, `HANDOVER_COMPLETED`, `ITEM_RETURNED`, `REVIEW_RECEIVED`, `REPORT_RECEIVED`, `SYSTEM` |
| `ReportTargetType` | `USER`, `REVIEW`, `ITEM` |
| `ReportStatus` | `OPEN`, `RESOLVED`, `DISMISSED` |
