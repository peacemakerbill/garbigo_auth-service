# Garbigo Auth Service API Reference

Context document for other Garbigo microservices. It describes every endpoint of the auth-service: URL, headers, request payloads, responses and errors.

- Stack: Spring Boot 4, Java 21, MongoDB, Redis, RabbitMQ, JWT
- Default local base URL: `http://localhost:8080` (replace with the real host)
- All bodies are JSON (`Content-Type: application/json`) unless marked multipart
- Dates and times are ISO-8601 UTC strings, for example `2026-10-09T08:30:00Z`
- Fields in responses appear in declaration order

## Contents

- Conventions
- Roles
- Authentication and tokens
- Auth endpoints
- Users and profile endpoints
- Live location
- Admin user management
- Social endpoints
- Profile view endpoints
- Account security endpoints
- Internal service-to-service endpoint
- Collector applications, applicant endpoints
- Collector applications, staff endpoints
- Collector application reference data
- Messaging (RabbitMQ)
- Quick call examples for other services

## Conventions

### Headers

| Header | When | Value |
|---|---|---|
| `Authorization` | Every endpoint except the public ones | `Bearer <jwt>` |
| `Content-Type` | Requests with a JSON body | `application/json` |
| `Content-Type` | File uploads | `multipart/form-data` (let the HTTP client set the boundary) |
| `X-Internal-Api-Key` | Only `/internal/**` | The shared service key (env `INTERNAL_API_KEY`) |

### Error shape

Every error, from controllers and from the security filters, has the same body:

```json
{ "message": "Human readable text written for end users" }
```

| Status | Meaning |
|---|---|
| 400 | Business rule or validation failure. Validation messages are joined by a space |
| 401 | Missing, expired, revoked or invalid token (or invalid internal key) |
| 403 | Authenticated but the role is not allowed, or a password confirmation was wrong |
| 404 | A device, session or other item that is not yours (it looks the same as one that does not exist) |
| 429 | Too many failed sign-in attempts. Includes a `Retry-After` header in seconds |
| 413 | Uploaded file too large |
| 500 | Unexpected failure: `Something went wrong on our end. Please try again later.` |

Typical 401 messages:

- `Your session has expired. Please sign in again.`
- `Your session is no longer valid. Please sign in again.`
- `Invalid internal API key`

Note: the live-location endpoints return `{ "error": "..." }` in a few cases instead of `message`. They are called out below.

Rate limiting: signup, signin, password reset request, resend verification and the public security links share a Redis based limiter (default 20 requests per minute, env `RATE_LIMIT_REQUESTS_PER_MINUTE`). When exceeded the endpoint returns 400 with a friendly message. The client address comes from `X-Forwarded-For` only when the request arrives from a proxy listed in `SECURITY_TRUSTED_PROXIES`. Repeated failed sign-ins are limited separately and return 429 (see POST /auth/signin).

## Roles

`CLIENT`, `COLLECTOR`, `ADMIN`, `OPERATIONS`, `FINANCE`, `SUPPORT`

- New accounts are always `CLIENT`. A role sent in a sign up request is ignored
- `ADMIN` is granted only to verified accounts whose email is listed in `INITIAL_ADMIN_EMAILS` or `AUTO_ADMIN_EMAILS`, or by another administrator (see docs/ACCOUNT_SECURITY.md)
- A role is never removed automatically. Only an administrator can change it
- `CLIENT` becomes `COLLECTOR` only through the collector application promote action (ADMIN)
- `OPERATIONS`, `FINANCE`, `SUPPORT` are staff roles (reviewers)
- Spring authorities are `ROLE_<name>`. The internal service key grants `ROLE_INTERNAL`

## Authentication and tokens

1. Sign up (public), then verify the email link.
2. Sign in to get a JWT.
3. Send `Authorization: Bearer <token>` on later calls.

JWT details:

- Signed HS-family secret (`JWT_SECRET`), lifetime from `JWT_EXPIRATION` (default 2592000 seconds, 30 days)
- Claims: `sub` (the user's email), `userId`, `jti`, `iat`, `exp`. The role is not in the token; it is read from the database on every request
- Every sign in also creates a server side session record keyed by `jti`. A session can be ended from the security endpoints, after a password change or reset, and on logout. Ending a session adds its `jti` to a Redis denylist, so the token is rejected even before it expires
- Using a token that was already signed out is recorded as a replay attempt and can trigger an alert email to the owner
- Tokens issued before session tracking existed keep working until they expire (env `SECURITY_ALLOW_UNTRACKED_TOKENS`)
- Another microservice that needs to trust a token should call this service, or validate the signature with the shared secret and also check the denylist

### AuthResponse (signin and social login)

```json
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "role": "CLIENT",
    "expiresAt": "2026-11-09T08:30:00Z"
}
```

## Auth endpoints

Base path: `/auth`

### POST /auth/signup (public)

Request:

```json
{
    "username": "billg",
    "firstName": "Bill",
    "middleName": "Graham",
    "lastName": "Peacemaker",
    "email": "bill@example.com",
    "phoneNumber": "+254712345678",
    "homeAddress": "Pipeline Estate, Nairobi",
    "password": "StrongPass123"
}
```

Required: `firstName`, `lastName`, `email` (valid), `password` (at most 128 characters). Optional: `username` (must be unique), `middleName`, `phoneNumber` (must be unique), `homeAddress`. The account is always created as `CLIENT`; a `role` field is ignored. The email is stored in lowercase and a different spelling by case counts as already registered.

Response 200:

```json
{ "message": "Your account has been created! Please check your email to verify your account." }
```

Errors (400):

- `First name is required`, `Last name is required`, `Email is required`, `Email must be a valid email address`, `Password is required`
- `This email is already registered. Please sign in or use a different email.`
- `This username is already taken. Please choose a different one.`
- `This phone number is already registered.`
- `We couldn't complete your sign up. Please try again.`

Side effects: a verification email (valid 24 hours) and a message on the RabbitMQ queue `user-created-queue` carrying the saved user without its password.

If `SECURITY_HIDE_ACCOUNT_EXISTENCE_ON_SIGNUP=true`, an already registered email gets the same 200 response and the existing owner receives a notice email instead of the error above.

### POST /auth/signin (public)

Request:

```json
{ "email": "bill@example.com", "password": "StrongPass123" }
```

Response 200: AuthResponse (see above).

The response also sets a device cookie (`gb_device`, HttpOnly) and an `X-Device-Token` header the first time a browser or app signs in. Native apps should store the header value and send it back as `X-Device-Token` on later sign ins so the device is recognised. Browsers send the cookie by themselves. If the sign in is from a new device, an unrecognised device or looks unusual, the owner receives an email with the time, IP address, approximate location, device, system, browser, sign in method and a link to report it.

Errors (400):

- `Invalid email or password`
- `Please verify your email before signing in. Check your inbox for the verification link, or request a new one if it has expired.`
- `This account has been closed. Please contact support for help.` (archived)
- `This account is currently inactive. Please contact support for help.`

The verification, closed and inactive messages are only shown when the correct password was entered. With a wrong password the answer is always `Invalid email or password`.

Error 429: `Too many sign-in attempts. Please wait a few minutes and try again.` with a `Retry-After` header. Limits apply per email and address, per address and per email, with a lockout that doubles on repeated lockouts up to a cap (see docs/ACCOUNT_SECURITY.md). The owner is emailed when an account gets locked.

### GET /auth/verify?token=<uuid> (public)

Response 200:

```json
{ "message": "Account verified successfully" }
```

If the address is on the administrator list, verification also grants `ADMIN`. By default (`SECURITY_ADMIN_VERIFICATION_REQUIRES_NEW_PASSWORD=true`) a password chosen before verification is discarded for such addresses and the message becomes `Your email is verified. Because this address has administrator access, we sent you a link to choose your password. Please use it to finish setting up your account.`

Errors (400): `This verification link isn't valid.`, `This verification link has expired. Please request a new one.`

### POST /auth/resend-verification (public)

Request:

```json
{ "email": "bill@example.com" }
```

Response 200 (always, whether or not the account exists): `{ "message": "If that email belongs to an account that still needs verifying, we have sent a new link to it." }`

A new link is not sent again within 60 seconds of the previous one.

### POST /auth/reset-password/request (public)

Request:

```json
{ "email": "bill@example.com" }
```

Response 200 (always, whether or not the account exists): `{ "message": "If an account exists for that email, we have sent a password reset link to it." }`. The link is valid for 1 hour and a new one is not sent within 60 seconds of the previous one.

### POST /auth/reset-password/confirm?token=<uuid> (public)

The token is a query parameter. Body:

```json
{ "newPassword": "NewStrongPass123", "confirmPassword": "NewStrongPass123" }
```

Response 200: `{ "message": "Password reset successfully" }`

All sessions of the account are signed out, other reset links are deleted, and the owner receives a confirmation email. Only a link of the reset type is accepted.

Errors (400): `This password reset link isn't valid.`, `This password reset link has expired. Please request a new one.`, `New password is required`, `Password confirmation is required`

### POST /auth/change-password (authenticated)

Request:

```json
{
    "oldPassword": "StrongPass123",
    "newPassword": "NewStrongPass123",
    "confirmPassword": "NewStrongPass123"
}
```

Response 200: `{ "message": "Password changed successfully" }`

All other sessions are signed out; the current one stays. The owner receives a confirmation email.

Errors (400): `Your current password is incorrect.`, `New passwords do not match.`

### POST /auth/logout (authenticated)

No body. Revokes the presented token and ends its session.

Response 200: `{ "message": "Logged out successfully" }`

### POST /auth/social/google, /auth/social/facebook, /auth/social/github (public)

Request (same for all three):

```json
{ "token": "<provider token>" }
```

- Google: the Google ID token (credential) from Google Sign-In
- Facebook: the Facebook access token
- GitHub: the GitHub access token

Response 200: AuthResponse, plus the device cookie and `X-Device-Token` header described under signin. A new account is created on first login (role `CLIENT`, verified).

How the provider email is trusted:

- Google: the ID token must say the email is verified
- GitHub: the primary verified email is read from GitHub. If that cannot be read, the profile email is used but is treated as unconfirmed
- Facebook: the email is always treated as unconfirmed
- An unconfirmed email never grants `ADMIN`, cannot create an account for an administrator address and cannot open an account that has a staff role or is on the administrator list
- If an existing account was never verified and the provider confirms the email, the account's password is removed and its sessions are ended, so a password planted before the real owner arrived cannot be used

Errors (400): `Token is required` plus provider specific friendly messages.

## Users and profile endpoints

Base path: `/users`. All need `Authorization: Bearer <token>`.

### UserDto (used in many responses)

```json
{
    "id": "66f1a2b3c4d5e6f708192a3b",
    "username": "billg",
    "firstName": "Bill",
    "middleName": "Graham",
    "lastName": "Peacemaker",
    "email": "bill@example.com",
    "phoneNumber": "+254712345678",
    "homeAddress": "Pipeline Estate, Nairobi",
    "profilePictureUrl": "https://res.cloudinary.com/.../avatar.jpg",
    "role": "CLIENT",
    "wastePreferences": "Recyclables",
    "collectionSchedule": "Weekly, Monday",
    "verified": true,
    "active": true,
    "archived": false
}
```

The password hash is never returned.

### GET /users/profile

Returns the signed-in user's UserDto. 401 if not signed in.

### PUT /users/profile

JSON body, all fields optional (only sent fields change):

```json
{
    "firstName": "Bill",
    "middleName": "Graham",
    "lastName": "Peacemaker",
    "email": "bill@example.com",
    "phoneNumber": "+254712345678",
    "homeAddress": "Pipeline Estate, Nairobi",
    "wastePreferences": "Recyclables",
    "collectionSchedule": "Weekly, Monday"
}
```

Response 200: UserDto.

### PUT /users/profile/picture

`multipart/form-data` with one part named `profilePicture` (an image file). The file is uploaded to Cloudinary.

Response 200: UserDto with the new `profilePictureUrl`.

Error (400): `Profile picture file is required`

### GET /users/collectors

Search collectors (any signed-in user). Query parameters, all optional: `search` (text), `verified` (true or false), `active`, `archived`.

Response 200: array of UserDto.

## Live location

### POST /users/live-location

Request:

```json
{ "latitude": -1.2921, "longitude": 36.8219 }
```

Response 200:

```json
{
    "message": "Live location updated successfully",
    "userId": "66f1a2b3c4d5e6f708192a3b",
    "latitude": -1.2921,
    "longitude": 36.8219
}
```

Errors use the key `error`: 400 `{ "error": "latitude and longitude are required" }`, 400 `{ "error": "Invalid latitude or longitude format" }`, 500 `{ "error": "Failed to update location: ..." }`.

The latest position is kept in Redis (for current lookups) and each update is also saved to MongoDB.

### GET /users/live-location/{userId}

Response 200 when the user has an active location:

```json
{
    "userId": "66f1a2b3c4d5e6f708192a3b",
    "firstName": "Bill",
    "middleName": "Graham",
    "lastName": "Peacemaker",
    "fullName": "Bill Graham Peacemaker",
    "email": "bill@example.com",
    "phoneNumber": "+254712345678",
    "profilePictureUrl": "https://res.cloudinary.com/.../avatar.jpg",
    "role": "COLLECTOR",
    "latitude": -1.2921,
    "longitude": 36.8219,
    "timestamp": "2026-10-09T08:30:00Z",
    "active": true
}
```

When there is none:

```json
{ "message": "No active live location found", "userId": "66f1...", "active": false }
```

## Admin user management

All need the `ADMIN` role (403 otherwise).

| Method | Endpoint | Body | Response |
|---|---|---|---|
| GET | `/users` | none. Query: `search`, `role`, `verified`, `active`, `archived` | array of UserDto |
| POST | `/users` | User fields (firstName, lastName, email, password, role, and so on) | UserDto |
| PUT | `/users/{id}` | User fields to change | UserDto |
| DELETE | `/users/{id}` | none | `{ "message": "User deleted" }` |
| PUT | `/users/{id}/archive` | none | `{ "message": "User archived" }` |
| PUT | `/users/{id}/unarchive` | none | `{ "message": "User unarchived" }` |
| PUT | `/users/{id}/activate` | none | `{ "message": "User activated" }` |
| PUT | `/users/{id}/deactivate` | none | `{ "message": "User deactivated" }` |
| PUT | `/users/{id}/verify` | none | `{ "message": "User verified" }` |
| PUT | `/users/{id}/unverify` | none | `{ "message": "User unverified" }` |

`role` filter values: `CLIENT`, `COLLECTOR`, `ADMIN`, `OPERATIONS`, `FINANCE`, `SUPPORT`.

Role changes through `PUT /users/{id}` are recorded in the audit log and the user is emailed. The last remaining admin cannot be demoted. Demoting an admin signs out all of their sessions. Changing a user's email marks it unverified, signs the user out, sends a verification link to the new address and a warning to the old one.

## Social endpoints

Base path: `/social`. All need `Authorization: Bearer <token>`.

`targetType` is a free text label (stored upper case) that defaults to `USER` when omitted. Other services can use their own values, for example `COLLECTOR` or `PICKUP`, with the target's id in `targetId`.

### UserSummaryDto

```json
{
    "id": "66f1...",
    "username": "billg",
    "firstName": "Bill",
    "middleName": "Graham",
    "lastName": "Peacemaker",
    "fullName": "Bill Graham Peacemaker",
    "profilePictureUrl": "https://res.cloudinary.com/.../avatar.jpg",
    "email": "bill@example.com",
    "phoneNumber": "+254712345678",
    "role": "COLLECTOR",
    "active": true,
    "currentLocation": {
        "userId": "66f1...",
        "latitude": -1.2921,
        "longitude": 36.8219,
        "timestamp": "2026-10-09T08:30:00Z"
    }
}
```

`currentLocation` is null when the user has no live location.

| Method | Endpoint | Body or query | Response |
|---|---|---|---|
| GET | `/social/profile/{userId}` | none | UserSummaryDto |
| POST | `/social/follow/{userId}` | none | text `Followed successfully` |
| DELETE | `/social/follow/{userId}` | none | text `Unfollowed successfully` |
| GET | `/social/followers/{userId}` | none | array of UserSummaryDto |
| GET | `/social/following/{userId}` | none | array of UserSummaryDto |
| GET | `/social/is-following/{userId}` | none | `{ "following": true }` |
| POST | `/social/like` | `{ "targetId": "...", "targetType": "USER" }` | text `Liked successfully` |
| DELETE | `/social/like` | `{ "targetId": "...", "targetType": "USER" }` | text `Unliked successfully` |
| GET | `/social/is-liked?targetId=...&targetType=USER` | query | `{ "liked": true }` |
| GET | `/social/likes/{targetId}?targetType=USER` | query | array of UserSummaryDto (who liked) |
| POST | `/social/review` | `{ "targetId": "...", "targetType": "USER", "rating": 5, "comment": "Great service" }` | text `Review submitted successfully` |
| PUT | `/social/review/{reviewId}` | `{ "rating": 4, "comment": "Updated" }` | text `Review updated successfully` |
| DELETE | `/social/review/{reviewId}` | none | text `Review deleted successfully` |
| GET | `/social/reviews/{targetId}?targetType=USER` | query | array of ReviewResponseDto |
| GET | `/social/reviewers/{targetId}?targetType=USER` | query | array of UserSummaryDto (who reviewed) |
| GET | `/social/stats/{userId}` | none | SocialStatsDto |

Several of these return plain text bodies (not JSON). The check responses serialize as `{ "following": true }` and `{ "liked": true }`.

ReviewResponseDto:

```json
{
    "id": "6710...",
    "reviewerId": "66f1...",
    "reviewerName": "John Doe",
    "reviewerProfilePictureUrl": "https://res.cloudinary.com/.../john.jpg",
    "rating": 5,
    "comment": "Great service",
    "createdAt": "2026-10-09T08:30:00"
}
```

SocialStatsDto:

```json
{ "followersCount": 12, "followingCount": 4, "likesCount": 30, "averageRating": 4.6 }
```

Errors (400): `You cannot follow yourself`, `You are already following this user`, `You are not following this user`, `You have already liked this user`, `You have not liked this user`, `Rating must be between 1 and 5`, `Review not found`, `You can only edit your own review`, `You can only delete your own review`, `User not found`.

## Profile view endpoints

Base path: `/profile-views`. All need a signed-in user in practice (the security config requires authentication for everything not listed as public).

| Method | Endpoint | Purpose | Response |
|---|---|---|---|
| POST | `/profile-views/{viewedUserId}` | Record that the caller viewed a profile. Optional headers `X-Forwarded-For` and `User-Agent` are stored | text `Profile view recorded successfully`; 400 text `Invalid profile ID` |
| GET | `/profile-views/stats/{userId}` | View statistics of a user | ProfileViewStatsDto |
| GET | `/profile-views/my-stats` | Statistics of the caller | ProfileViewStatsDto |
| GET | `/profile-views/who-viewed-me` | Who visited the caller's profile | array of UserSummaryDto |
| GET | `/profile-views/who-i-viewed` | Profiles the caller visited | array of UserSummaryDto |

ProfileViewStatsDto:

```json
{
    "totalViews": 120,
    "uniqueViewers": 45,
    "todayViews": 6,
    "recentViewers": [
        {
            "viewerId": "66f1...",
            "viewerName": "John Doe",
            "viewerProfilePictureUrl": "https://res.cloudinary.com/.../john.jpg",
            "viewedAt": "2026-10-09T08:30:00Z",
            "anonymous": false
        }
    ]
}
```

## Account security endpoints

Base path: `/security`. Everything here needs `Authorization: Bearer <token>` except the two link endpoints marked public. A device or session that belongs to someone else returns 404 exactly like one that does not exist.

### DeviceDto

```json
{
    "id": "6710...",
    "label": "Chrome 126 on Windows 10 or 11",
    "deviceType": "Desktop",
    "osName": "Windows",
    "browserName": "Chrome",
    "firstSeenAt": "2026-10-01T08:00:00Z",
    "lastSeenAt": "2026-10-20T09:29:00Z",
    "lastIp": "203.0.113.9",
    "lastLocation": "Nairobi, Kenya",
    "signInCount": 7,
    "trusted": false,
    "trustedAt": null,
    "current": true,
    "activeSessions": 1
}
```

`lastLocation` is approximate and may be null. Device details come from what the browser or app reports and can be wrong.

### SessionDto

```json
{
    "id": "6710...",
    "deviceId": "6710...",
    "deviceLabel": "Chrome 126 on Windows 10 or 11",
    "createdAt": "2026-10-20T09:29:00Z",
    "lastSeenAt": "2026-10-20T09:40:00Z",
    "expiresAt": "2026-11-19T09:29:00Z",
    "ip": "203.0.113.9",
    "location": "Nairobi, Kenya",
    "signInMethod": "PASSWORD",
    "current": true
}
```

### SignInActivityDto

```json
{
    "id": "6710...",
    "success": true,
    "failureReason": null,
    "occurredAt": "2026-10-20T09:29:00Z",
    "signInMethod": "PASSWORD",
    "ip": "203.0.113.9",
    "location": "Nairobi, Kenya",
    "deviceLabel": "Chrome 126 on Windows 10 or 11",
    "deviceStatus": "NEW",
    "riskLevel": "LOW"
}
```

`deviceStatus` is `NEW`, `RECOGNIZED` or `TRUSTED`. `riskLevel` is `LOW`, `MEDIUM` or `HIGH`.

### Endpoints

| Method | Endpoint | Body | Response |
|---|---|---|---|
| GET | `/security/devices` | none | array of DeviceDto |
| GET | `/security/devices/{id}` | none | `{ "device": DeviceDto, "recentSignIns": [SignInActivityDto] }` |
| POST | `/security/devices/{id}/trust` | `{ "password": "..." }` | DeviceDto |
| DELETE | `/security/devices/{id}/trust` | none | DeviceDto |
| DELETE | `/security/devices/{id}` | none | `{ "message": "...", "count": <sessions ended> }` |
| POST | `/security/devices/revoke-remembered` | none | `{ "message": "...", "count": <devices> }` |
| GET | `/security/sessions` | none | array of SessionDto |
| DELETE | `/security/sessions/{id}` | none | `{ "message": "That session was signed out." }` |
| POST | `/security/sessions/revoke-others` | none | `{ "message": "...", "count": <sessions ended> }` |
| GET | `/security/activity?limit=20` | none | array of SignInActivityDto (newest first, at most 100) |
| POST | `/security/actions/inspect` (public) | `{ "token": "..." }` | what the link refers to, with a masked IP |
| POST | `/security/actions/report-unauthorized` (public) | `{ "token": "...", "requestPasswordReset": true }` | `{ "message", "sessionsEnded", "deviceRemoved", "passwordResetEmailSent" }` |
| GET | `/security/admin/events?userId=&type=&page=0&size=50` (ADMIN) | none | page of security events |
| POST | `/security/admin/users/{userId}/revoke-sessions` (ADMIN) | none | `{ "message", "count" }` |

Notes:

- Trusting a device asks for the account password. Accounts without a password (social sign in only) must have signed in within the last `SECURITY_RECENT_AUTH_MINUTES` minutes. A wrong password counts toward the sign in limits. Trust never replaces authentication: a trusted device still has to sign in. It only means fewer alert emails
- `DELETE /security/devices/{id}` forgets the device and signs out every session that was started from it
- `POST /security/devices/revoke-remembered` makes this service forget every remembered browser and app of the account; the next sign in from each one is treated as a new device
- Signing out sessions can be switched off with `SECURITY_SESSION_REVOCATION_ENABLED=false` (403)
- `/security/actions/*` are the targets of the "this wasn't me" link in alert emails. The token is single use, expires (default 24 hours), is stored only as a hash and is limited like other public endpoints. `inspect` never uses the link up. `report-unauthorized` signs out the reported session (all sessions by default), forgets the reported device and, unless `requestPasswordReset` is false, emails a password reset link. It never changes the password itself. An invalid, used or expired link always gets the same message: `This link is no longer valid. If you are worried about your account, sign in and review your recent activity.`

## Internal service-to-service endpoint

For other Garbigo microservices only. Authenticated purely by the shared key. A user's own JWT does not open it, whatever the role.

### GET /internal/users

Headers:

```
X-Internal-Api-Key: <INTERNAL_API_KEY>
```

Response 200: array of InternalUserDto

```json
[
    {
        "id": "66f1a2b3c4d5e6f708192a3b",
        "username": "billg",
        "firstName": "Bill",
        "middleName": "Graham",
        "lastName": "Peacemaker",
        "email": "bill@example.com",
        "phoneNumber": "+254712345678",
        "homeAddress": "Pipeline Estate, Nairobi",
        "profilePictureUrl": "https://res.cloudinary.com/.../avatar.jpg",
        "role": "COLLECTOR",
        "wastePreferences": "Recyclables",
        "collectionSchedule": "Weekly, Monday",
        "verified": true,
        "active": true,
        "archived": false,
        "followers": ["66f2..."],
        "likes": ["66f3..."],
        "reviews": ["6710..."],
        "liveLocations": ["6711..."],
        "createdAt": "2026-09-01T10:00:00",
        "updatedAt": "2026-10-01T12:00:00"
    }
]
```

Errors: 401 `{ "message": "Invalid internal API key" }` when the header is present but wrong. Missing header gives 401 or 403 from Spring Security.

### GET /internal/users/{email}/details

Used by the wallet service. Same `X-Internal-Api-Key` header. The email is a path variable.

Response 200:

```json
{
    "id": "66f1a2b3c4d5e6f708192a3b",
    "email": "bill@example.com",
    "role": "CLIENT",
    "active": true,
    "verified": true,
    "firstName": "Bill",
    "middleName": "Graham",
    "lastName": "Peacemaker",
    "fullName": "Bill Graham Peacemaker"
}
```

404 with an empty body when the email is unknown.

### GET /internal/users/validate-email/{email}

Used by the wallet's M-Pesa C2B validation before crediting a wallet. Always 200.

```json
{ "valid": true }
```

```json
{ "valid": false, "reason": "NOT_FOUND" }
```

`reason` is one of `NOT_FOUND`, `ARCHIVED`, `INACTIVE`, `UNVERIFIED`.

## Collector applications, applicant endpoints

Base path: `/collector-applications`. Need `Authorization: Bearer <token>`. Used by a `CLIENT` who wants to become a collector. Users who are already `COLLECTOR`, or staff, cannot apply.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/collector-applications/options` | Reference data for forms (countries, ID types, and so on) |
| GET | `/collector-applications/requirements?vehicleType=&serviceTypes=&idType=` | Documents needed for the chosen vehicle, services and ID type |
| GET | `/collector-applications/me` | Current application, or the invitation to start one |
| GET | `/collector-applications/me/history` | All the applicant's past applications |
| PUT | `/collector-applications/me` | Save details (creates the draft on first call) |
| POST | `/collector-applications/me/documents` | Upload one document (multipart) |
| DELETE | `/collector-applications/me/documents/{documentId}` | Remove a document |
| GET | `/collector-applications/me/documents/{documentId}/file` | Download the applicant's own file |
| POST | `/collector-applications/me/submit` | Submit or resubmit |
| POST | `/collector-applications/me/withdraw` | Withdraw. Optional body `{ "reason": "..." }` |

### Workflow

Normal path: `DRAFT`, then `SUBMITTED`, then `PROCESSING`, then `VERIFIED`, then `ACCEPTED`.

- Staff can move an application to `MORE_INFO_NEEDED` (the applicant fixes it and resubmits, which returns it to `SUBMITTED`) or to `REJECTED`
- The applicant can withdraw from any open state, which gives `WITHDRAWN`

Statuses: `DRAFT`, `SUBMITTED`, `PROCESSING`, `MORE_INFO_NEEDED`, `VERIFIED`, `ACCEPTED`, `REJECTED`, `WITHDRAWN`. `ACCEPTED` is set only by the promote action. The applicant can edit only in `DRAFT` and `MORE_INFO_NEEDED`. One open application per person.

### PUT /collector-applications/me

Only sent fields change. Omit or send `null` to keep a value. Send `""` to clear a text field. Everything is optional while drafting.

```json
{
    "countryCode": "KE",
    "idType": "NATIONAL_ID",
    "idNumber": "12345678",
    "taxId": "A123456789B",
    "dateOfBirth": "1994-03-21",
    "alternatePhone": "0712345678",
    "region": "Nairobi",
    "city": "Nairobi",
    "postalCode": "00100",
    "physicalAddress": "Pipeline Estate, Block C",
    "timeZone": "Africa/Nairobi",
    "serviceTypes": ["GENERAL_WASTE", "RECYCLABLES"],
    "vehicleType": "PICKUP_TRUCK",
    "vehicleRegistration": "KDA 123A",
    "capacityValue": 1200,
    "capacityUnit": "KILOGRAMS",
    "serviceAreas": ["Embakasi", "Donholm", "Umoja"],
    "maxTravelDistanceKm": 25,
    "helpersCount": 2,
    "availableDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"],
    "shiftStart": "06:00",
    "shiftEnd": "17:00",
    "availableForEmergency": true,
    "yearsOfExperience": 4,
    "experienceSummary": "Four years collecting household waste in Embakasi.",
    "languages": ["English", "Kiswahili"],
    "motivation": "I want a steady, formal way to grow my business.",
    "payoutMethod": "MOBILE_MONEY",
    "payoutProvider": "M-Pesa",
    "payoutAccountNumber": "0712345678",
    "payoutAccountName": "John Wanjiku",
    "emergencyContact": { "name": "Jane Wanjiku", "relationship": "Sister", "phone": "+254700111222" },
    "references": [
        { "name": "Peter Otieno", "relationship": "Estate chairman", "phone": "+254722333444" }
    ],
    "acceptedTerms": true,
    "consentToBackgroundCheck": true,
    "confirmsInfoIsTrue": true
}
```

Field rules:

| Field | Rule |
|---|---|
| `countryCode` | Two-letter ISO code (`KE`, `US`, `IN`). Send first: it is the default region for parsing phone numbers |
| `idType` | `NATIONAL_ID`, `PASSPORT`, `RESIDENCE_PERMIT`, `OTHER_GOVERNMENT_ID` |
| `idNumber` | Stored upper case. Cannot be used in two live applications from different accounts |
| `dateOfBirth` | `yyyy-MM-dd`, must be 18 or older |
| `alternatePhone`, `payoutAccountNumber` (mobile money), contact phones | Any valid international number. Saved in E.164, for example `+254712345678` |
| `serviceTypes` | `GENERAL_WASTE`, `RECYCLABLES`, `ORGANIC_WASTE`, `BULK_AND_CONSTRUCTION_WASTE`, `SEWAGE_EXHAUSTER` |
| `vehicleType` | `HANDCART`, `MOTORBIKE`, `TUKTUK`, `PICKUP_TRUCK`, `LORRY`, `EXHAUSTER_TRUCK` |
| `vehicleRegistration` | Required for motorised vehicles (all except `HANDCART`). Stored upper case |
| `capacityUnit` | `KILOGRAMS`, `LITRES`, `CUBIC_METRES` |
| `serviceAreas` | Up to 10 |
| `maxTravelDistanceKm` | 1 to 200 |
| `helpersCount` | 0 to 20 |
| `availableDays` | `MONDAY` to `SUNDAY` |
| `shiftStart`, `shiftEnd` | `HH:mm` |
| `yearsOfExperience` | 0 to 60 |
| `experienceSummary`, `motivation` | Up to 1000 characters |
| `languages` | Up to 8 |
| `physicalAddress` | Up to 200 characters |
| `payoutMethod` | `MOBILE_MONEY`, `BANK_ACCOUNT`, `DIGITAL_WALLET` |
| `references` | Up to 3, optional |
| `acceptedTerms`, `consentToBackgroundCheck`, `confirmsInfoIsTrue` | Must all be `true` to submit |

Response 200: the application view (applicant version):

```json
{
    "id": "6710f2c1a4b2c93d5e8f1a20",
    "referenceNumber": "GCA-2026-000001",
    "status": {
        "code": "DRAFT",
        "label": "Draft",
        "message": "Your application is saved but not sent yet. Finish the steps and submit it when you are ready."
    },
    "editable": true,
    "canSubmit": false,
    "canWithdraw": true,
    "lastNoteFromTeam": null,
    "nextSteps": ["Upload your ID document (front)"],
    "progress": {
        "percent": 67,
        "readyToSubmit": false,
        "missing": ["Upload your ID document (front)"],
        "sections": [
            { "name": "Personal details", "done": true },
            { "name": "Documents", "done": false }
        ]
    },
    "applicantName": "Bill Graham",
    "applicantEmail": "bill@example.com",
    "applicantPhone": "+254712345678",
    "details": { "idNumber": "12345678", "countryCode": "KE", "payoutAccountNumber": "+254712345678" },
    "requirements": [],
    "documents": [],
    "timeline": [
        { "status": "DRAFT", "title": "Application started", "note": null, "actorName": "You", "at": "2026-10-09T08:30:00Z" }
    ],
    "submittedAt": null,
    "updatedAt": "2026-10-09T08:31:12Z",
    "createdAt": "2026-10-09T08:30:00Z"
}
```

`details` carries every saved field from the request above. `requirements` is the checklist for this application (see the checklist item shape below).

### GET /collector-applications/me (no application yet)

```json
{
    "hasApplication": false,
    "canApply": true,
    "message": "Become a Garbigo collector. The application takes about 10 minutes, and you can save your progress and come back any time.",
    "application": null,
    "checklist": [
        {
            "type": "ID_FRONT",
            "label": "ID document (front)",
            "description": "The front of your ID card or residence permit, or the photo page of your passport.",
            "tip": "Place it on a flat, plain surface in good light. All four corners should be visible.",
            "required": true,
            "multiple": false,
            "acceptedFormats": "PDF, JPG or PNG",
            "maxFileSizeMb": 5,
            "uploaded": false,
            "reviewStatus": null
        }
    ]
}
```

When an application exists, `hasApplication` is true and `application` holds the view above.

### POST /collector-applications/me/documents

`multipart/form-data`:

| Part | Required | Notes |
|---|---|---|
| `type` | Yes | A document type (table below) |
| `file` | Yes | PDF, JPG or PNG, up to 5 MB. The real content is checked, not just the extension |
| `documentNumber` | No | For example a licence number |
| `expiryDate` | No | `yyyy-MM-dd`. Expired dates are refused |

Response 200: the full updated application view, with the document in `documents`:

```json
{
    "id": "0f4c1c6e-77a9-4d2f-b0c4-1c0f6e2f9a11",
    "type": "ID_FRONT",
    "typeLabel": "ID document (front)",
    "fileName": "id-front.jpg",
    "mimeType": "image/jpeg",
    "sizeBytes": 482113,
    "sizeLabel": "471 KB",
    "documentNumber": null,
    "expiryDate": null,
    "uploadedAt": "2026-10-09T08:35:10Z",
    "reviewStatus": "PENDING",
    "reviewStatusLabel": "Waiting for review",
    "reviewNote": null,
    "reviewedByName": null,
    "reviewedAt": null,
    "fileUrl": "/collector-applications/me/documents/0f4c1c6e-77a9-4d2f-b0c4-1c0f6e2f9a11/file"
}
```

Uploading the same type again replaces the old file unless that document is already verified. Verified documents cannot be replaced or deleted by the applicant. `OTHER` allows up to 5 files.

Errors (400 or 413):

- `Please upload a PDF, JPG or PNG file. If your phone saved the photo in HEIC format, change the camera setting to 'Most compatible' and take the photo again.`
- `That file is too large. Please upload a file smaller than 5 MB.` or `That file is too large. Please upload a smaller file.` (413)
- `We couldn't reach document storage right now. Please try again in a few minutes.`

### GET .../file endpoints

Return the binary file with its content type. Files live in Google Drive and are never public; they are streamed through this service after a role check.

### POST /collector-applications/me/submit

No body. Response 200: application view with status `SUBMITTED`.

Error example:

```json
{ "message": "Almost there. Before you submit: Upload your Passport photo; Confirm that your information is true." }
```

### POST /collector-applications/me/withdraw

Optional body `{ "reason": "I found another job" }`. Response 200: application view with status `WITHDRAWN`.

### Other common errors

| Situation | Message |
|---|---|
| Editing after submitting | Your application is with our team, so it can't be edited right now. If we need anything, we will email you. You can also withdraw it and start again. |
| Under 18 | You must be at least 18 years old to apply. |
| Already a collector | Your account is already a collector account. You do not need to apply again. |
| Duplicate ID number | This ID number is already used in another application. |

## Collector applications, staff endpoints

Roles: `ADMIN`, `OPERATIONS`, `FINANCE`, `SUPPORT`. Promote is `ADMIN` only.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/collector-applications` | Search and filter the queue |
| GET | `/collector-applications/stats` | Counts for a dashboard |
| GET | `/collector-applications/{id}` | Full staff view |
| POST | `/collector-applications/{id}/claim` | Assign to me. Moves `SUBMITTED` to `PROCESSING` |
| PATCH | `/collector-applications/{id}/status` | Change status |
| PATCH | `/collector-applications/{id}/documents/{documentId}/review` | Verify or reject a document |
| GET | `/collector-applications/{id}/documents/{documentId}/file` | Download an uploaded file |
| POST | `/collector-applications/{id}/notes` | Add an internal note (never shown to the applicant) |
| POST | `/collector-applications/{id}/promote` | Make the applicant a collector (ADMIN only) |

### GET /collector-applications (search)

| Query parameter | Example | Notes |
|---|---|---|
| `q` | `wanjiku` | Matches name, email, phone, reference, ID number, tax ID |
| `status` | `SUBMITTED,PROCESSING` | One or more. Drafts hidden unless `DRAFT` is requested |
| `vehicleType` | `PICKUP_TRUCK` | |
| `country` | `KE` | Two-letter code |
| `region` | `Nairobi` | |
| `serviceType` | `SEWAGE_EXHAUSTER` | |
| `unassigned` | `true` | Unclaimed applications |
| `assignedTo` | `me` | Or a user id |
| `submittedFrom`, `submittedTo` | `2026-10-01` | Inclusive dates |
| `sortBy` | `submittedAt` | `createdAt`, `updatedAt`, `submittedAt`, `referenceNumber`, `status`, `applicantName` |
| `direction` | `asc` | `asc` or `desc` |
| `page`, `size` | `0`, `20` | Size capped at 100 |

Response 200:

```json
{
    "content": [
        {
            "id": "6710f2c1a4b2c93d5e8f1a20",
            "referenceNumber": "GCA-2026-000001",
            "status": { "code": "SUBMITTED", "label": "Submitted", "message": "We have received your application. A team member will start reviewing it soon." },
            "applicantName": "Bill Graham",
            "applicantEmail": "bill@example.com",
            "applicantPhone": "+254712345678",
            "maskedIdNumber": "12****78",
            "countryCode": "KE",
            "country": "Kenya",
            "region": "Nairobi",
            "city": "Nairobi",
            "vehicleType": "Pickup truck",
            "serviceTypes": ["Household and general waste", "Recyclables"],
            "documentsUploaded": 8,
            "documentsVerified": 0,
            "documentsRequired": 8,
            "completionPercent": 100,
            "assignedToName": null,
            "daysWaiting": 2,
            "submittedAt": "2026-10-07T09:12:00Z",
            "updatedAt": "2026-10-07T09:12:00Z"
        }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
}
```

### GET /collector-applications/stats

```json
{
    "total": 42,
    "byStatus": { "DRAFT": 9, "SUBMITTED": 6, "PROCESSING": 4, "MORE_INFO_NEEDED": 3, "VERIFIED": 2, "ACCEPTED": 15, "REJECTED": 2, "WITHDRAWN": 1 },
    "awaitingReview": 6,
    "unassigned": 5,
    "submittedLast7Days": 8,
    "oldestWaitingDays": 3
}
```

`total` excludes drafts.

### GET /collector-applications/{id}

```json
{
    "id": "6710f2c1a4b2c93d5e8f1a20",
    "referenceNumber": "GCA-2026-000001",
    "status": { "code": "PROCESSING", "label": "In review", "message": "Our team is checking your details and documents right now." },
    "account": {
        "userId": "66f1...",
        "fullName": "Bill Graham",
        "email": "bill@example.com",
        "phoneNumber": "+254712345678",
        "role": "CLIENT",
        "profilePictureUrl": "https://res.cloudinary.com/...",
        "accountActive": true
    },
    "details": { },
    "progress": { "percent": 100, "readyToSubmit": true, "missing": [], "sections": [] },
    "requirements": [],
    "documents": [
        {
            "id": "0f4c1c6e-77a9-4d2f-b0c4-1c0f6e2f9a11",
            "type": "ID_FRONT",
            "typeLabel": "ID document (front)",
            "reviewStatus": "PENDING",
            "fileUrl": "/collector-applications/6710f2c1a4b2c93d5e8f1a20/documents/0f4c1c6e-77a9-4d2f-b0c4-1c0f6e2f9a11/file"
        }
    ],
    "allRequiredDocumentsVerified": false,
    "blockers": ["ID document (front) is waiting for review"],
    "allowedNextStatuses": [
        { "code": "MORE_INFO_NEEDED", "label": "More information needed" },
        { "code": "VERIFIED", "label": "Verified" },
        { "code": "REJECTED", "label": "Not approved" }
    ],
    "canPromote": false,
    "assignedToId": "66a0...",
    "assignedToName": "Mary Achieng",
    "timeline": [],
    "internalNotes": [],
    "submissionCount": 1,
    "submittedAt": "2026-10-07T09:12:00Z",
    "decidedAt": null,
    "updatedAt": "2026-10-09T07:50:00Z",
    "createdAt": "2026-10-07T08:40:00Z"
}
```

`canPromote` is true only when the application is `VERIFIED` and the viewer is an `ADMIN`. ID numbers are shown in full here, masked in lists.

### POST /collector-applications/{id}/claim

No body. Response 200: staff view. Starts the review and emails the applicant.

### PATCH /collector-applications/{id}/status

```json
{
    "status": "MORE_INFO_NEEDED",
    "note": "The back of your ID is blurry. Please upload a clearer photo and submit again.",
    "internalNote": "Second time this applicant has uploaded a blurry ID."
}
```

- `note` is shown to the applicant and emailed. `internalNote` is optional and staff-only
- `MORE_INFO_NEEDED` and `REJECTED` require `note`
- `ACCEPTED` cannot be set here (use promote)
- `VERIFIED` is refused until every required document is verified and the details are complete

Response 200: staff view.

Errors (400):

- `Please tell the applicant why the application is not approved (at least a short sentence). They will see this note.`
- `Verify every required document first. Still open: ID document (front) is waiting for review.`

### PATCH /collector-applications/{id}/documents/{documentId}/review

```json
{ "status": "REJECTED", "note": "The photo is cut off at the bottom. Please include the whole card." }
```

`status`: `VERIFIED`, `REJECTED` or `PENDING` (undo). Rejecting needs a note. The first review of a `SUBMITTED` application starts the review automatically. Response 200: staff view.

### POST /collector-applications/{id}/notes

```json
{ "note": "Called the reference, Peter Otieno confirms the applicant. All good." }
```

Response 200: staff view.

### POST /collector-applications/{id}/promote (ADMIN)

Body optional:

```json
{ "note": "Welcome to the team. Please keep your live location on while you work." }
```

In one request: the application must be `VERIFIED`, the user's role changes `CLIENT` to `COLLECTOR`, the status becomes `ACCEPTED`, a timeline entry is added and the applicant is emailed. Refused when the account is already a collector, is staff, or is deactivated or archived.

Response 200: staff view.

Error: `Verify the application first. Move it to Verified once every document has been checked, then try again.`

## Collector application reference data

Document types (`type` in uploads):

| Type | Label | Required when |
|---|---|---|
| `ID_FRONT` | ID document (front, or passport photo page) | Always |
| `ID_BACK` | ID document (back) | Unless `idType` is `PASSPORT` |
| `PASSPORT_PHOTO` | Passport-style photo (JPG or PNG only) | Always |
| `TAX_CERTIFICATE` | Tax registration document | Optional |
| `GOOD_CONDUCT_CERTIFICATE` | Police clearance certificate | Always |
| `DRIVING_LICENCE` | Driving licence | Motorised vehicle |
| `VEHICLE_REGISTRATION` | Vehicle registration document | Motorised vehicle |
| `INSURANCE_CERTIFICATE` | Vehicle insurance certificate | Motorised vehicle |
| `WASTE_PERMIT` | Waste transport permit | Services include `SEWAGE_EXHAUSTER` |
| `PROOF_OF_ADDRESS` | Proof of address | Optional |
| `OTHER` | Other supporting document | Optional, up to 5 |

Document review statuses: `PENDING`, `VERIFIED`, `REJECTED`.

GET `/collector-applications/options` returns (shortened):

```json
{
    "countries": [ { "code": "KE", "name": "Kenya", "dialCode": "+254" } ],
    "idTypes": [ { "code": "NATIONAL_ID", "label": "National ID card" } ],
    "payoutMethods": [ { "code": "MOBILE_MONEY", "label": "Mobile money" } ],
    "vehicleTypes": [ { "code": "PICKUP_TRUCK", "label": "Pickup truck", "motorized": true } ],
    "serviceTypes": [ { "code": "GENERAL_WASTE", "label": "Household and general waste" } ],
    "capacityUnits": [ { "code": "KILOGRAMS", "label": "Kilograms" } ],
    "daysOfWeek": [ { "code": "MONDAY", "label": "Monday" } ],
    "statuses": [ { "code": "DRAFT", "label": "Draft" } ],
    "uploadLimits": { "maxFileSizeMb": 5, "acceptedFormats": "PDF, JPG or PNG" },
    "documents": []
}
```

`documents` is the baseline checklist (same item shape as `checklist` above). Label wording can change, so use this endpoint as the source of truth for dropdowns.

## Messaging (RabbitMQ)

- Queue: `user-created-queue` (env `RABBITMQ_QUEUE_USER_CREATED`)
- Published after a successful signup. The payload is the saved user object serialized as JSON with the `password` field set to null. It still contains personal data, so a consuming service should avoid logging it and should prefer fetching users through `/internal/users` if it only needs profile data

## Quick call examples for other services

Sign in and call an authenticated endpoint:

```
POST {base}/auth/signin
Content-Type: application/json

{ "email": "bill@example.com", "password": "StrongPass123" }
```

```
GET {base}/users/profile
Authorization: Bearer <token>
```

Internal fetch of all users:

```
GET {base}/internal/users
X-Internal-Api-Key: <INTERNAL_API_KEY>
```

Upload a document:

```
POST {base}/collector-applications/me/documents
Authorization: Bearer <token>
Content-Type: multipart/form-data

type=ID_FRONT
file=@id-front.jpg
```
