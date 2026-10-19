# Account security and admin provisioning

This document explains how administrator accounts are provisioned, how sign in alerts, devices and sessions work, how to configure them and how to test them. The API itself is described in `AUTH_SERVICE_API.md` (section "Account security endpoints").

## 1. Administrator accounts

No administrator, password or secret is written in the code; accounts come only from `.env` (see "Creating admin accounts from configuration" below). Two settings decide who can become `ADMIN`. Both take a comma separated list. Each entry is trimmed, lowercased, checked for a valid address and de-duplicated. Invalid entries are skipped, logged with a masked address and recorded in the audit log, and the rest of the list still works. Wrapping quotes and `;` or spaces as separators are tolerated.

| Setting | What it does |
|---|---|
| `INITIAL_ADMIN_EMAILS` | Applied on every start. A verified, active account with a listed address becomes `ADMIN`. If no account exists, an invitation email is sent asking the owner to register and verify that address (at most one invitation per `SECURITY_ADMIN_INVITATION_RESEND_DAYS`, default 7). |
| `AUTO_ADMIN_EMAILS` | Nothing happens at start. Any account that proves it owns a listed address becomes `ADMIN` at that moment: when it verifies its email, or when it signs in with a provider that confirms the email. |

Both lists together are "eligible addresses". Changing either list needs an application restart, because the values are read once at startup.

Example `.env`:

```
INITIAL_ADMIN_EMAILS=owner@garbigo.com
AUTO_ADMIN_EMAILS=ops.lead@garbigo.com,cto@garbigo.com
```

### Creating admin accounts from configuration

For a first deployment you can have the service create the administrator accounts itself. Fill up to three slots in `.env` (the same names are listed in `.env.example`):

```
ADMIN_1_EMAIL=owner@garbigo.com
ADMIN_1_USERNAME=owner
ADMIN_1_FIRST_NAME=Bill
ADMIN_1_MIDDLE_NAME=Graham
ADMIN_1_LAST_NAME=Peacemaker
ADMIN_1_PHONE=0712345678
ADMIN_1_PASSWORD=a-long-one-time-password-2026
```

- A slot is used only when it has a valid email and a password of at least `ADMIN_SEED_MIN_PASSWORD_LENGTH` characters (default 8) containing letters and numbers. Anything else is skipped with a warning that names the slot and a masked email, never the password.
- The account is created as a verified, active `ADMIN`. A missing first name becomes "Admin". A missing username is generated from the name, and a taken one gets a number added.
- If the email already has an account, nothing is changed, including its password. Changing the value in `.env` later has no effect on an existing account.
- The seeded email is added to `INITIAL_ADMIN_EMAILS` automatically, so no invitation is sent and it does not appear in the drift warning.
- The password is stored only as a hash. Treat the value in `.env` as a one time password: sign in, change it with `POST /auth/change-password`, then remove it from `.env`. Keep `.env` out of git.
- Set `ADMIN_SEED_ENABLED=false` to turn the whole feature off.

### What makes a grant safe

- The account must be verified, active and not archived. Registering with an admin address does nothing until the mailbox owner clicks the verification link.
- Social sign in only counts when the provider confirms the email. Google must report `email_verified`. GitHub uses the primary verified email. Facebook is never treated as confirmed, so it can never grant admin, never create an account for an admin address and never open a privileged account.
- A role is changed with a single conditional database update, so two instances starting together cannot grant twice or send two notices. Running the provisioning again changes nothing and sends nothing.
- A sign up request cannot choose a role. The `role` field was removed from the sign up request and the service always creates a `CLIENT`.
- Protection against someone registering your admin address first: when an eligible address is verified for the first time, the password chosen at sign up is discarded and the owner is emailed a link to choose a new one (`SECURITY_ADMIN_VERIFICATION_REQUIRES_NEW_PASSWORD`, default true). Without this, whoever registered the address first would keep the password after the real owner verified it.
- Every grant is written to the audit log (`ADMIN_ROLE_GRANTED`) and the account owner receives an email.
- A failure to send email, to reach Redis or to read one account never stops the application from starting.

### Revocation policy

Roles are never removed automatically. Removing an address from the lists does not demote anyone, because silently downgrading the only administrator after a typo would lock everyone out. Instead:

- At every start the service logs a warning and records `ADMIN_CONFIG_DRIFT` when admin accounts exist that are not on either list.
- To demote someone, an administrator changes the role (`PUT /users/{id}`). That action is audited, the person is emailed, all of their sessions are signed out and the last remaining admin cannot be demoted.
- An administrator can also sign out every session of a user (`POST /security/admin/users/{userId}/revoke-sessions`).

### Moving away from the old built in admin

The old `AdminUserInitializer` created a fixed admin account with a fixed password. Delete that file; `AdminSeedRunner` replaces it and reads everything from `.env`. If your database already contains that account, it still exists and its password is public in your git history. After your real admin is provisioned, deactivate that account or change its password, then rotate anything that account could reach.

## 2. Sign in alerts

### Email templates

Every email template lives in `src/main/resources/templates/emails/` and shares one look through `layout.html`:

| File | Used for |
|---|---|
| `layout.html` | Shared pieces: page head, banner, buttons, link box, notice box, details rows, steps and footer. Change the brand colours or footer here once. |
| `verify-account.html` | Email verification |
| `reset-password.html` | Password reset link |
| `application-update.html` | Collector application status updates |
| `security-notice.html` | All security notices (sign-ins, role changes, lockouts and so on) |

Each page picks a tone (`success`, `info`, `warning`, `danger` or `neutral`) that sets its colours. No images or emoji are used, so nothing depends on remote images being loaded. The old `verify-account.html` and `reset-password.html` in the root of `templates/` must be deleted after you copy the new ones in.


After each successful sign in the service checks, in the background, whether the owner should be told. The sign in itself never waits for this and never fails because of it.

| Situation | Email |
|---|---|
| First time this browser or app is seen for the account | "New sign-in to your account" |
| Known browser that is not marked trusted | "Sign-in to your account" (controlled by `SECURITY_NOTIFY_UNTRUSTED_DEVICES`) |
| Looks unusual (see section 4) | "Unusual sign-in activity" |
| Trusted device with no unusual signs | No email |

The email contains the time with its time zone, IP address, approximate location, device, operating system, browser, device type, sign in method, whether the device is new, recognised or trusted, a button to report it and the line "If you did not sign in, secure your account immediately by revoking this session and changing your password." Every email also has a plain text version. The email never contains a password, a token that gives access, or a password hash. The report link carries a single use token that only lets the owner start the report flow.

Duplicates are prevented three ways: a sign in record that already produced an alert is never processed again, the same address and device combination is not emailed twice inside `SECURITY_NOTIFICATION_COOLDOWN_MINUTES` (default 60), and unusual-activity alerts for one account are spaced by `SECURITY_SUSPICIOUS_ALERT_COOLDOWN_MINUTES` (default 15).

Other emails use the same template: administrator invitation, administrator access added, role changed, password changed, password reset completed, email address changed (sent to the old address), account locked, "this wasn't me" completed, a signed-out session being reused and "someone tried to register with your email".

### Settings

```
SECURITY_LOGIN_EMAIL_NOTIFICATIONS=true     # master switch for sign in emails
SECURITY_NEW_DEVICE_ALERTS=true
SECURITY_NOTIFY_UNTRUSTED_DEVICES=true
SECURITY_SUSPICIOUS_LOGIN_ALERTS=true
SECURITY_DISPLAY_TIME_ZONE=Africa/Nairobi   # zone shown in emails
```

### Client address and location

- The client address is `request.getRemoteAddr()`. `X-Forwarded-For` is used only if the direct connection comes from an address or range listed in `SECURITY_TRUSTED_PROXIES`, and the header is read from right to left, skipping trusted proxies. If you run behind a load balancer, list its range. If you do not list it, every user will look like the load balancer.
- Location is optional (`SECURITY_IP_GEOLOCATION_ENABLED`, off by default). When on, the public IP is sent to the URL in `SECURITY_IP_GEOLOCATION_URL` (an ipinfo style JSON endpoint; add `SECURITY_IP_GEOLOCATION_TOKEN` if the service needs one). Private and local addresses are never sent. Results are cached for 6 hours and a failed lookup simply leaves the location out. Turn this on only if sharing visitor IP addresses with that provider fits your privacy policy.
- The location is approximate and comes from the IP address. The emails say so.

## 3. Devices and sessions

- A device is recognised by an opaque random 256 bit token. Browsers receive it as the HttpOnly cookie `gb_device` (secure, `SameSite` configurable, 180 days). Native apps read the `X-Device-Token` response header after sign in and send it back as the `X-Device-Token` request header. Only the SHA-256 hash is stored, with a unique index, so a database leak does not reveal usable tokens. A token that belongs to a different account is ignored.
- This is recognition, not a fingerprint. The service does not claim to identify hardware. Clearing cookies makes the browser look new, and the service makes no stronger promise.
- Trusting a device only reduces alert emails. It never skips a password, never extends a session and can be undone at any time.
- Every sign in creates a session record keyed by the token's `jti`. Sessions can be listed and ended one at a time or all except the current one. Ending a session adds its `jti` to the Redis denylist, and the JWT filter also checks the stored session on every request, so an ended session stops working immediately, including after a Redis restart.
- Password change signs out the other sessions (the current one stays). Password reset, email change, a social account being linked over an unverified password account, demotion of an admin and "this wasn't me" sign out all sessions.
- Sensitive device actions (trusting a device) ask for the password again. For accounts with no password the sign in must be recent (`SECURITY_RECENT_AUTH_MINUTES`).
- At most `SECURITY_MAX_DEVICES_PER_USER` devices are remembered per account; the oldest untrusted one is dropped first.
- If `SECURITY_DEVICE_COOKIE_SAME_SITE=None` is needed (front end on another site) also keep `SECURITY_DEVICE_COOKIE_SECURE=true`. The CORS configuration already allows credentials; for browsers to send the cookie cross-site the front end must call with credentials enabled, and the allowed origins should be narrowed from `*` to your real front end.

## 4. Suspicious sign in detection and throttling

Failed sign ins are counted in Redis. Keys use a hash of the email, never the email itself.

| Counter | Default limit | Window |
|---|---|---|
| same email from the same address | 5 | 15 minutes |
| one address, any email | 30 | 15 minutes |
| one email, any address | 20 | 15 minutes |

When a limit is reached the answer is `429` with `Retry-After`. The first lockout lasts 5 minutes and doubles on each repeat inside 24 hours up to 60 minutes (`SECURITY_MAX_FAILED_LOGINS_*`, `SECURITY_FAILURE_WINDOW_MINUTES`, `SECURITY_LOCKOUT_MINUTES`, `SECURITY_MAX_LOCKOUT_MINUTES`). A successful sign in clears the pair counter. The owner is emailed when their account is locked, and `ACCOUNT_LOCKED` is audited. Wrong passwords, unknown emails and disabled accounts all give the same message unless the correct password was supplied, so the answer does not reveal whether an account exists.

A successful sign in is also scored. Points add up from: new device (20), many recent failures (40), several different addresses (25), unusually frequent sign ins (20), a different country than last time (30), a different region (10), and +15 more for administrator accounts once there is another signal. Under 40 is `LOW`, 40 to 59 `MEDIUM`, 60 and above `HIGH`. A medium or high score produces the "unusual activity" email. Detection never blocks a sign in; it only alerts. Thresholds: `SECURITY_SUSPICIOUS_FAILED_THRESHOLD` (5), `SECURITY_SUSPICIOUS_ADDRESS_THRESHOLD` (3), `SECURITY_SUSPICIOUS_FREQUENCY_THRESHOLD` (10), `SECURITY_SUSPICIOUS_WINDOW_MINUTES` (60). Reuse of a token that was already signed out is recorded (`TOKEN_REPLAY_ATTEMPT`) once per token, and three distinct attempts in an hour email the owner (once per 6 hours).

## 5. "This wasn't me"

The alert email links to `{APP_URL}{SECURITY_REPORT_PATH}?token=...` (default `/account/security/report`). Your front end page at that address should:

1. Call `POST /security/actions/inspect` with the token and show the time, device, approximate location and a partly hidden IP address, so the owner can recognise the sign in.
2. After the owner presses a confirm button, call `POST /security/actions/report-unauthorized` with `{ "token": "...", "requestPasswordReset": true }`.

The token is random (256 bits), stored only as a hash, valid for `SECURITY_ACTION_TOKEN_TTL_MINUTES` (default 24 hours) and consumed by one atomic database update, so two clicks, or two tabs, cannot both succeed. Opening the link, previewing it or inspecting it changes nothing. Confirming signs out the reported session (all sessions unless `SECURITY_REPORT_REVOKES_ALL_SESSIONS=false`), forgets the reported device, emails a password reset link (never changes the password itself), writes `UNAUTHORIZED_SIGN_IN_REPORTED` to the audit log and sends the owner a "your account has been secured" email. Any invalid, used or expired link gets one generic message.

## 6. Stored data, indexes and retention

New MongoDB collections (created and indexed automatically by the existing `SafeIndexInitializer`, which creates indexes for every `@Document` class while `auto-index-creation` stays off, so there is no manual migration):

| Collection | Content | Notable indexes |
|---|---|---|
| `user_devices` | remembered browsers and apps, token hash, trust state | unique `tokenHash`; `userId + lastSeenAt` |
| `user_sessions` | one record per issued token (`jti`), revocation state | unique `jti`; `userId + revokedAt`; `expiresAt` |
| `sign_in_records` | successful and failed sign ins with device, address and risk | `userId + occurredAt` |
| `security_events` | audit log | `type + subjectHash + occurredAt`; `userId`; `occurredAt` |
| `security_action_tokens` | "this wasn't me" tokens, hashed | unique `tokenHash`; `userId`; `expiresAt` |

`users` gains one hidden field, `tokensValidAfter`, which is never returned in API responses. Existing users need no change.

Retention runs as a scheduled cleanup (not database expiry), starting five minutes after boot and then every `SECURITY_RETENTION_INTERVAL_MINUTES` (default 360). Audit events, sign in records and devices unused for `SECURITY_AUDIT_RETENTION_DAYS` (default 180) are deleted. Expired sessions are deleted after at most 30 days. Action tokens are deleted a day after expiry. Audit records for e-mail based events store a hash of the address, not the address.

## 7. Testing the workflow

Prerequisites: the service running with working SMTP credentials, Redis and MongoDB, and `SECURITY_DEVICE_COOKIE_SECURE=false` only if you test a browser over plain `http` outside localhost.

Administrator provisioning:

1. Set `INITIAL_ADMIN_EMAILS=you@example.com` and restart. With no account yet, an invitation arrives and the log says `Administrator invitation queued`.
2. Register with that address (`POST /auth/signup`), click the verification link. Because the address is eligible, you receive a second email to choose your password, and the account is `ADMIN`. Use the reset link, then sign in.
3. Restart again. The log says `granted=0, alreadyAdmin=1` and no email is sent.
4. Register a second account with an address not on either list and verify it. The role stays `CLIENT`.

Sign in alerts:

1. Sign in from Postman. You get the token plus a `Set-Cookie: gb_device=...` and an `X-Device-Token` header. A "New sign-in" email arrives with an IP address and device label.
2. Sign in again within the hour, sending that cookie or header. No email arrives, because the same address and device label was already notified inside the cooldown.
3. Set `SECURITY_NOTIFICATION_COOLDOWN_MINUTES=1`, restart, wait a minute and sign in again with the cookie. You get the "Sign-in to your account" notice (known device that is not trusted). Sign in without the cookie and you get "New sign-in" again. Call `POST /security/devices/{id}/trust` with your password, sign in with the cookie once more, and no email arrives.
4. Send five wrong passwords for the same email within 15 minutes. The next attempt, even with the correct password, answers `429` with `Retry-After`, and you receive the "too many failed attempts" email. After the lockout ends, a successful sign in from a new device triggers the "unusual activity" email.
5. Open the "report" link from an alert email, call `inspect`, then `report-unauthorized`. Call the same link again and see the generic invalid message. Use the old token on `GET /users/profile` and see `401 You have been signed out. Please sign in again.`

Automated tests: `mvn test` runs the unit tests under `src/test/java`. They need no database, Redis or mail server (services are tested with mocks, the email template is rendered with real Thymeleaf).

## 8. Limitations

- The tests for the Spring layer were written against the real APIs but were not run by the author in the delivery environment because Maven Central was not reachable there. Run `mvn test` first and send any failures back. The pure logic classes were compiled and their 89 tests were run.
- IP location is approximate, often wrong for mobile networks and VPNs, and is off unless you enable it. VPN or proxy use can raise or lower risk scores.
- Device recognition depends on the cookie or header being kept. It is not hardware identification and does not claim to be.
- There is no refresh token. A session lasts as long as the JWT (`JWT_EXPIRATION`, default 30 days) unless it is ended. Consider shortening it.
- No multi factor authentication is implemented. Detection only alerts; it does not ask for a second factor.
- Tokens issued before this update are untracked. They keep working until they expire, can be ended through password change, reset or the denylist, but do not show up in the session list. Set `SECURITY_ALLOW_UNTRACKED_TOKENS=false` once you accept signing everyone out.
- Facebook sign in is weaker than the others because Facebook does not guarantee that the email was verified. It can still sign in to a normal verified account with the same email; it cannot touch admin or staff accounts.
- Sign up tells a visitor when an email is already registered unless `SECURITY_HIDE_ACCOUNT_EXISTENCE_ON_SIGNUP=true`. The setting trades a clearer sign up error for privacy.
- Existing accounts whose stored email differs only by upper or lower case are found by a case-insensitive lookup, but new accounts are stored in lowercase. Two old accounts that differ only by case are treated as ambiguous: sign in and social sign in refuse them until one is removed.
- Lockouts can be triggered by someone else typing wrong passwords for your email (per email limit of 20 from any address). The lock is short, capped at 60 minutes, and the owner is told by email. This is the usual trade-off of lockouts.
- A save of a user object that was loaded before a password reset can overwrite the hidden `tokensValidAfter` value. Only untracked legacy tokens are affected.
- The email template renders on the server; its look was not checked in real mail clients.
- Detecting a stolen token is limited to the reuse of a signed-out token. A stolen token that is still valid looks the same as its owner.
- The retention job runs on every instance. That is harmless but duplicated.
