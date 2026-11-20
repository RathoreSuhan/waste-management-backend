# 🇮🇳 Clean Bharat
### AI-Powered Smart Waste Management Platform

> **Clean Bharat** is a modern AI-powered waste management platform that leverages **Artificial Intelligence, Computer Vision, Cloud Computing, and Community Participation** to create a transparent, efficient, and intelligent garbage reporting and cleanup ecosystem.
>
> The platform connects **Citizens**, **Cleaners**, **Municipal Corporations**, and **Administrators** through a unified digital system where garbage reports are **AI-validated**, duplicate reports are automatically prevented, cleanup work is verified using **Google Gemini Vision AI**, and successful cleanups are transparently showcased to the public.
>
> Frontend companion: **[waste-management-frontend](https://github.com/RathoreSuhan/waste-management-frontend#readme)**.

---

## 🌍 Vision

To build a **smart, transparent, and AI-driven waste management ecosystem** that empowers citizens to actively participate in keeping cities clean while enabling municipal authorities to manage sanitation operations more efficiently through intelligent automation.

Rather than functioning as just another complaint management system, **Clean Bharat** transforms waste management into a collaborative digital platform where every stage—from reporting to cleanup verification—is supported by Artificial Intelligence and data-driven decision making.

---

# 🎯 Project Objectives

The primary objectives of Clean Bharat are:

- 🤖 Eliminate fake and misleading garbage reports using AI image validation.
- 📍 Prevent duplicate reports through intelligent geolocation-based detection.
- 🏛 Bridge the communication gap between citizens and municipal corporations.
- 🧹 Provide a structured workflow for cleaners to manage cleanup assignments.
- ✅ Verify completed cleanups using AI instead of relying solely on manual approval.
- 🏆 Encourage cleaner participation through a reward and leaderboard system.
- 💬 Promote community participation using voting and discussion features.
- 📊 Generate analytics for smarter report prioritization.
- 🌐 Maintain complete transparency through a public feed of verified cleanups.

---

# ❓ Problem Statement

Traditional waste management systems face several real-world challenges:

- Citizens often submit duplicate reports for the same garbage location.
- Fake or irrelevant images reduce the reliability of complaints.
- Municipal authorities struggle to prioritize reports efficiently.
- Completed cleanups are difficult to verify manually.
- Citizens have little visibility into whether reported garbage has actually been cleaned.
- Existing systems rarely encourage community engagement or cleaner accountability.

These limitations result in slower response times, inefficient resource allocation, and reduced public trust.

---

# 💡 Our Solution

Clean Bharat addresses these challenges by integrating **Artificial Intelligence** into every critical stage of the waste management lifecycle.

Instead of accepting every submitted report blindly, the platform intelligently validates reports, verifies completed cleanups, prevents duplicate submissions, and rewards genuine cleanup efforts.

The result is a smarter, more transparent, and highly scalable waste management platform capable of supporting real municipal operations.

---

# 🚀 Key Highlights

### 🤖 AI-Powered Image Validation

Every garbage image uploaded by a citizen is verified using **Google Gemini Vision AI** before the report is accepted.

Validation includes:

- Garbage detection
- Image quality verification
- AI-generated image detection
- Garbage category identification
- Severity estimation
- Confidence scoring

---

### 📍 Intelligent Duplicate Report Detection

Duplicate reports are prevented using a multi-stage location matching algorithm that combines:

- Pincode filtering
- Time-window filtering
- Bounding Box optimization
- Haversine Distance calculation

Instead of creating another report, citizens are redirected to the existing report where they can support it through voting and discussion.

---

### 🧹 AI-Based Cleanup Verification

After a cleaner uploads the cleanup image, Google Gemini Vision compares the **Before** and **After** images to verify:

- Same physical location
- Similar surroundings
- Garbage successfully removed
- Fake cleanup detection
- Camera angle similarity
- Confidence threshold

AI verification alone does **not** close a cleanup. An AI-verified cleanup only moves to `AWAITING_APPROVAL`, where the city Municipal Corporation gives the final sign-off.

---

### 🏆 Reward & Leaderboard System

Cleaners earn reward points only after their assigned Municipal Corporation approves completion. GPS proof and AI verification are prerequisites; the service suppresses sequential duplicate rewards with an assignment-existence check, without a database uniqueness/locking guarantee for concurrent calls.

The platform automatically generates:

- Reward History
- National Leaderboard
- State Leaderboard
- District Leaderboard (all ULBs in one State/District pair)
- City Leaderboard (retained for backward compatibility)
- Badge System (Bronze, Silver, Gold)

---

### 📊 Community Analytics

The platform continuously analyzes community engagement using:

- Citizen voting
- Discussion activity
- Replies
- Engagement Score

This enables intelligent report prioritization and trending report discovery.

---

### 🌐 Public Transparency

Every cleanup officially signed off by its Municipal Corporation is published to a public feed where anyone can view:

- Before & After images
- Cleaner details
- Municipal Corporation
- Likes
- Views
- Shares

This creates accountability while showcasing successful cleanup efforts.

---

# 🏗 Complete System Workflow

```text
Citizen
    │
    ▼
Upload Garbage Image
    │
    ▼
Google Gemini AI Validation
    │
    ▼
Duplicate Report Detection
    │
    ▼
Cloudinary Image Storage
    │
    ▼
Garbage Report Created
    │
    ▼
Assigned to the City Municipal Corporation
    │
    ▼
Cleaner Inspects Site & Submits Cleanup Proposal
    │
    ▼
Municipal Proposal Decision (Approve / Reject / Request Revision)
    │
    ▼
Cleaner Starts Cleanup (GPS proof within 50 m)
    │
    ▼
Optional Activity Log Updates
    │
    ▼
Cleaner Uploads Cleanup Proof (GPS + After Image)
    │
    ▼
Google Gemini Cleanup Verification
    │
    ▼
Awaiting Municipal Completion Review
    │
    ▼
Municipal Completion Sign-off
    │
    ▼
Reward Generated (Repeat Check)
    │
    ▼
Report Marked Resolved
    │
    ▼
Public Feed Updated
    │
    ▼
Leaderboard Updated
```

---

# 🧠 AI Workflow

Artificial Intelligence is deeply integrated into two critical stages of the platform.

## Phase 1 — Garbage Report Validation

```text
Citizen Uploads Image
        │
        ▼
MultipartFile
        │
        ▼
Read Image Bytes
        │
        ▼
Detect MIME Type
        │
        ▼
Convert to Base64
        │
        ▼
Google Gemini Vision AI
        │
        ▼
Structured JSON Response
        │
        ▼
Business Rule Validation
        │
        ├─────────────► Reject Report
        │
        ▼
Duplicate Detection
        │
        ├─────────────► Existing Report
        │
        ▼
Cloudinary Upload
        │
        ▼
Accept Report
```

---

## Phase 2 — Cleanup Verification

```text
Cleaner Uploads After Image
        │
        ▼
Cloudinary Upload
        │
        ▼
Download Before & After Images
        │
        ▼
Convert Images to Base64
        │
        ▼
Google Gemini Vision AI
        │
        ▼
Compare Images
        │
        ▼
Verify
    Same Location
    Garbage Removed
    Confidence Score
        │
        ├────────────► Reject Cleanup
        │
        ▼
AI Verification Passed
        │
        ▼
AWAITING_APPROVAL
        │
        ▼
Municipal Completion Review
        │
        ├────────────► REWORK_REQUIRED → New GPS + AI Proof
        │
        ▼
COMPLETED (Municipal Sign-off)
        │
        ▼
Reward Cleaner
        │
        ▼
Publish to Public Feed
```

---

# ✨ Core Features

| Module | Description |
|---------|-------------|
| 🔐 Authentication | Secure JWT-based authentication with role-based authorization, email-OTP verification and Google sign-in |
| 📝 Garbage Reporting | Citizens can report garbage with images, verified GPS and either directory-backed location codes or the legacy city/state shape |
| ☁️ Cloudinary Integration | Cloud storage for garbage and cleanup images |
| 📍 Smart Location Module | LGD State → District → ULB validation, canonical names, ULB-code routing and legacy compatibility |
| 🏛 Municipal Management | Admin-managed municipal corporations mapped to official ULB codes without rewriting legacy city values |
| ⭐ Community Voting | Citizens prioritize reports through a 5-star urgency system |
| 💬 Community Discussion | Threaded comments with unlimited nested replies |
| 📊 Engagement Analytics | Trending reports and dashboard analytics |
| 🧹 Cleaner Workflow | Proposal, municipal assignment, GPS-verified execution, AI assessment and municipal completion |
| 🤖 AI Cleanup Verification | Google Gemini Vision verifies completed cleanups |
| 🏆 Reward System | Reward history and cleaner recognition |
| 🌐 Public Feed | Public showcase of officially completed, AI-verified cleanups |
| 🥇 Live Leaderboard | National, State, District and City rankings; personal ranking for signed-in cleaners |
| 👨‍💼 Admin Portal | User management, report moderation, analytics, and deletion workflows |

---

# 🛠 Technology Stack

## Backend

| Technology | Purpose |
|------------|----------|
| Java 21 | Primary Programming Language |
| Spring Boot 3.5.15 | Backend Framework |
| Spring Security | Authentication & Authorization |
| JWT | Stateless Authentication |
| Spring Data JPA | ORM Layer |
| Hibernate | Database Persistence |
| PostgreSQL | Relational Database |
| Redis | In-Memory Cache for Dashboards & Leaderboards |
| Spring Data Redis + Spring Cache | Declarative Caching (`@Cacheable` / `@CacheEvict`) with Lettuce client |
| Redis + Lua | Distributed API Rate Limiting (atomic fixed-window counter) |
| Apache Commons Pool2 | Redis Connection Pooling |
| Maven | Dependency Management |
| Lombok | Boilerplate Code Reduction |
| OpenFeign | External AI API Communication |
| Spring Boot Starter Mail | Six-digit sign-up verification codes via SMTP (Gmail STARTTLS on 587) |
| Google API Client | Server-side Google ID-token verification for Google sign-in |
| JUnit 5 | Unit & Integration Testing Framework |
| Mockito | Mocking Framework for Unit Testing |
| H2 Database | In-Memory Database for Integration Testing |
| JaCoCo | Code Coverage Analysis |

---

The POM targets Java **21**, imports Spring Cloud **2025.0.0**, and explicitly
pins Lombok **1.18.46**, JJWT **0.12.7**, Google API Client **2.7.0**, Cloudinary
HTTP44 **1.39.0** and JaCoCo **0.8.13**. Other dependency versions are managed
by the Spring Boot parent or Spring Cloud BOM; the Maven wrapper targets **3.9.16**.

## Artificial Intelligence

| Technology | Purpose |
|------------|----------|
| Google Gemini Vision AI | Garbage Detection & Cleanup Verification |

---

## Cloud Services

| Technology | Purpose |
|------------|----------|
| Cloudinary | Secure Cloud Image Storage |
| Brevo | Transactional email over HTTPS — verification-code fallback where SMTP ports are blocked |

---

## Development Tools

- IntelliJ IDEA
- PostgreSQL
- pgAdmin
- Postman
- Git
- GitHub
- vscode

---

# 🏛 Backend Architecture

The backend of **Clean Bharat** follows a **production-oriented Layered Architecture** built with **Spring Boot**, with clear separation of responsibilities.

```text
                Client (React)
                      │
                      ▼
             REST Controllers
                      │
                      ▼
              Service Layer
         (Business Logic & AI)
                      │
                      ▼
            Repository Layer
                      │
                      ▼
              PostgreSQL Database
```

Each layer has a dedicated responsibility:

| Layer | Responsibility |
|--------|----------------|
| Controller | Handles HTTP requests and responses |
| Service | Contains business rules, AI integrations and workflow logic |
| Repository | Interacts with PostgreSQL using Spring Data JPA |
| Entity | Models domain tables and relationships |
| DTO | Provides secure data transfer between backend and frontend |
| Security | Handles JWT authentication and authorization |
| Exception | Provides centralized error handling |

This architecture keeps controllers lightweight, encapsulates business logic inside services, and ensures that database access remains isolated from presentation concerns. It promotes scalability, maintainability, testability and clean separation of concerns.

The backend is fully functional and production-oriented, supporting secure authentication, AI-powered validation, intelligent report management, community engagement, cleaner workflows, administrative tools, and public transparency.

---

# 📂 Backend Modules

The backend is divided into multiple independent modules, each responsible for a specific domain of the application.

---

## 🔐 Authentication & Authorization

Authentication is implemented using **Spring Security** and **JWT (JSON Web Token)**.

A new self-service account can only be created with an email address somebody has **proved they can read**. There are two proofs, and they differ in nothing else — both end in the same account row and the same JWT:

| Proof | How | Works for |
|---|---|---|
| **Emailed code** | A six-digit code is sent to the typed address and typed back | Every provider — Gmail, Yahoo, Outlook, college and workplace domains |
| **Google Sign-In** | The address arrives inside a token Google signed, carrying `email_verified` | Google accounts only |

> **Why a code and not a lookup.** There is no API that will say whether a typed Gmail, Yahoo or Outlook address exists. Those lookups were withdrawn by every major provider because they let anyone enumerate accounts, and probing a mail server directly is both blocked at that scale and treated as abuse. Delivering something to the mailbox is the only check that works everywhere — and the only one that proves *ownership* rather than mere existence.

The sign-up this replaced simply took a typed address on trust.

### Features

- Email verification codes for sign-up (any provider)
- Google Sign-In (Google Identity Services → server-side ID-token verification)
- Automatic linking of a Google account to an existing Clean Bharat account
- BCrypt Password Encryption
- Stateless Authentication
- Role-Based Authorization (RBAC)
- JWT Token Generation & Validation
- Protected REST APIs

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Sign up with a password. Called twice — see below |
| `POST` | `/api/auth/google` | Verify a Google credential; sign in, link, or report that registration is still needed |
| `POST` | `/api/auth/google/register` | Create an account from a verified Google identity plus the role/location Google cannot supply |
| `POST` | `/api/auth/login` | Email + password sign-in |

All four sit under `permitAll` in `SecurityConfig` — a visitor signing in has no token yet — and all four are in the **`auth` rate-limit tier** (10 requests/minute, keyed by IP).

`/register` is one endpoint with two outcomes, because the body is the same either way:

- **without** `verificationCode` → the form is validated, a code is emailed, and the answer is `verificationRequired: true`. Nothing is created. Validating first means a request that was going to be refused never costs an email.
- **with** `verificationCode` → the form is validated *again*, the code is checked, the account is created and a JWT comes back. Re-validating matters: the first call's "this address is free" answer is minutes old by then.

The code itself lives in the Redis the application already runs — hashed, under a fingerprint of the address, with a ten-minute TTL, a five-guess cap and a sixty-second resend cooldown. See `EmailVerificationService`. Nothing is parked on the server between the two calls.

### Supported Roles

| Role | Description |
|------|-------------|
| 👤 Citizen | Reports garbage, votes, comments, tracks reports |
| 🧹 Cleaner | Proposes cleanups, executes approved work, uploads cleanup proof |
| 🏛 Municipal Corporation | Approves proposals and signs off completed cleanups assigned to its authenticated corporation only |
| 👨‍💼 Admin | Complete platform management |

Both sign-up routes accept **only** `ROLE_CITIZEN` and `ROLE_CLEANER`. `ROLE_ADMIN` and `ROLE_MUNICIPAL_OFFICER` are rejected with 403.

A Municipal Corporation is **not** a self-registered account. It is a row in the `municipal_corporations` table created by an Admin, and only the email stored on that row can sign in to the Municipal Console. A corporation's address is also refused by both sign-up routes.

> The `MUNICIPAL` cleaner type only describes the kind of cleanup crew. It never grants Municipal Console access.

Authentication Flow

```text
         Sign-up form                      Google Sign-In button
              │                                      │
              ▼                                      ▼
   POST /api/auth/register                  Google ID token
     (no code yet)                                   │
              │                                      ▼
              ▼                           POST /api/auth/google
   six-digit code emailed                           │
              │                                      ▼
              ▼                      GoogleIdTokenVerifier
   POST /api/auth/register             signature · iss · aud · exp
     (+ code)                          + email_verified == true
              │                                      │
              ▼                                      ▼
   code checked against Redis         sub match → sign in
              │                        email match → link, sign in
              │                        neither → POST /google/register
              │                                      │
              └──────────────┬───────────────────────┘
                             ▼
                    account row created
                             │
                             ▼
                 Clean Bharat JWT generated
                             │
                             ▼
                 Authorization: Bearer <JWT>
                             │
                             ▼
                 JwtAuthenticationFilter
                             │
                             ▼
                 Authenticated Request (RBAC)
```

The token issued after Google sign-in is **the same Clean Bharat JWT** the password path issues — same subject, same expiry, same filter, same role rules. Google's own token is verified, read and discarded; it is never stored and never used to authorize an API call.

### Why linking on a matching address is safe

When a Google sign-in finds no `google_subject` but a verified address that matches an existing row, it links the two rather than creating a duplicate. That would be dangerous if an account could be registered to an address its owner had never seen — whoever got there first would be handed the real owner's account. Every route above proves the address before the row exists, which is what closes it.

### Configuration

| Variable | Required | Default | Purpose |
|---|:---:|---|---|
| `MAIL_USERNAME` | For password sign-up | — | SMTP account the verification codes are sent from (trimmed) |
| `MAIL_PASSWORD` | For SMTP path | — | **Secret.** A Gmail app password. Spaces are stripped in `MailConfig` (Google displays it grouped) |
| `MAIL_HOST` | No | `smtp.gmail.com` | SMTP host (trimmed) |
| `MAIL_PORT` | No | `587` | Submission port; STARTTLS is enabled **and required** in `MailConfig` |
| `MAIL_FROM` | No | `MAIL_USERNAME` | Gmail rejects a From it did not authenticate; also used as the Brevo sender |
| `BREVO_API_KEY` | For production free tier | — | **Secret.** Brevo transactional-email API key. When set, codes are sent via Brevo HTTPS API (port 443) instead of SMTP |
| `BREVO_API_URL` | No | `https://api.brevo.com/v3/smtp/email` | Brevo send endpoint override |
| `BREVO_SENDER_NAME` | No | `Clean Bharat` | Display name used as the Brevo sender |
| `GOOGLE_CLIENT_ID` | For Google sign-in | — | OAuth 2.0 **Web application** client id. Resolved as the `google.client-id` property, so the environment variable alone is enough |

With `MAIL_USERNAME` unset, password sign-up is **refused** with a message naming the missing configuration — it fails closed rather than creating an account with an unproven address, and there is deliberately no "log the code instead" developer shortcut. Google sign-in is unaffected and remains available.

**Render free tier and SMTP.** Since September 2025 Render free web services block outbound SMTP ports `25`/`465`/`587`, so Gmail SMTP always times out there even with correct `MAIL_*` values (local delivery still works, and Google sign-in is unaffected because it already uses HTTPS/443). When `BREVO_API_KEY` is set, `EmailVerificationService` sends the same code via the Brevo HTTPS API (port 443) using `RestClient` with 10-second timeouts instead of `JavaMailSender`; the Brevo sender address must be verified in the Brevo dashboard. Without the key the SMTP path is used (local development and paid instances). Send failures log the exception plus its root cause (never the address, code, or key) so Render logs distinguish a network block from bad credentials.

With backend `GOOGLE_CLIENT_ID` unset, Google sign-in is refused. The frontend
hides its Google button independently when `VITE_GOOGLE_CLIENT_ID` is unset.
Password sign-up is unaffected.

There is **no `GOOGLE_CLIENT_SECRET`**. The browser hands over an ID token rather than an authorization code, so there is nothing to exchange and nothing confidential to configure.

**Google Cloud Console** — create an OAuth 2.0 Client ID of type *Web application* and set its **Authorized JavaScript origins** to the frontend origins (the same list `SecurityConfig` allows through CORS):

```
http://localhost:5173
https://waste-management-frontend-xi.vercel.app
https://cleanbharat.tech
https://www.cleanbharat.tech
```

**Authorized redirect URIs: none.** This flow has no browser redirect back to the backend.

The backend remains completely **stateless**, making it suitable for scalable distributed deployments.

---

# 📝 Garbage Report Management

The Garbage Report Module forms the core of the application.

Every report contains structured information rather than a simple text location.

Each report stores:

- Title
- Description
- Garbage Image
- Latitude
- Longitude
- Address
- Landmark
- City
- State
- District
- ULB Code
- Pincode
- Current Status
- Urgency Score
- Engagement Score
- Citizen Information

The reporting workflow is:

```text
Citizen
      │
      ▼
Location Resolved            (directory cascade; legacy free text
      │                       keeps the old path)
      ▼
Municipal Destination        (code-backed reports: resolved by ULB
Check                        code BEFORE any upload; a ULB nobody
      │                      has onboarded is refused here with an
      ▼                      actionable error and no partial report)
Upload Image
      │
      ▼
AI Validation
      │
      ▼
Duplicate Detection
      │
      ▼
Cloudinary Upload
      │
      ▼
Garbage Report Created
      │
      ▼
Cleanup Assignment           (code-backed reports carry the already-
                              resolved destination; legacy reports are
                              routed by stored city, case-insensitively)
```

The report becomes the central entity used by almost every module in the application.

---

# 📍 Location Model: State → District → ULB

Every place in Clean Bharat comes from the LGD-based directory
(`locations/india-ulb-directory.json`, served by `GET /api/locations`).
Registration validates a `stateCode` / `districtCode` / `ulbCode` triple;
the location refactor makes reporting, routing, cleaner eligibility and the
district leaderboard use the same verified hierarchy.

## Directory contract

The directory is generated deterministically from the Government of India LGD
ULB export, not maintained as a handwritten list:

| Artifact | Repository path |
|---|---|
| Source workbook | `src/main/resources/locations/source/Ulb_By_State_2026-10-18_16-34-45.xlsx` |
| Generated directory | `src/main/resources/locations/india-ulb-directory.json` |
| Generator | `scripts/build_location_directory.py` |
| Validator/resolver | `service/location/IndianLocationService` |
| Public endpoint | `GET /api/locations` (`LocationController`) |

The validated generated file contains **36 State/UT codes** (including the
supplemental Lakshadweep entry), **752 ULB-linked district records**, **5,059
unique ULB codes**, **5,094 district/ULB rows**, **18 ULB types**, and zero
duplicate state/district/ULB triples. These are directory-validation results,
not production database row counts. The workbook is a ULB export: a State/UT
may exist in the generated state list without having selectable district/ULB
rows, so the UI does not claim that every State/UT has an available municipal
area.

`GET /api/locations` returns one nested document:

```text
{
  version,
  source,
  counts,
  states: [{
    code, name,
    districts: [{
      code, name,
      ulbs: [{ code, name, type }]
    }]
  }]
}
```

The endpoint is public (registration needs it before a JWT exists) and sends
`Cache-Control: max-age=86400, public`. `IndianLocationService` parses and
indexes the classpath resource once at application startup; this is HTTP/browser
caching, not Redis caching. `resolve(stateCode, districtCode, ulbCode)` returns
`IndianLocationService.ResolvedLocation(state, district, city, ulbCode)` with
canonical names. `LocationUtil` only normalizes name shape. Missing or malformed
directory data fails closed through `LocationDataUnavailableException` with
HTTP `503` rather than accepting arbitrary client-supplied names.

Both `RegisterRequest` and `GoogleRegisterRequest` require the three codes.
Password and Google registration share the same resolver, so neither route can
store a cross-state district, an arbitrary city string, or a ULB outside its
selected district.

## Implementation and rollout status

> **Source default.** The frontend enables the report cascade and District tab
> only when the build-time value is exactly
> `VITE_LOCATION_V2_ENABLED="true"`; an unset value or any other spelling uses
> the legacy report form and hides the District tab. Registration and municipal
> mapping are directory-backed independently of this flag.

> **Operator-verified production record — 28 October 2026.** The production
> frontend flag is `true`; stages T0–T5 below were completed; all 18 original
> reconciliation entries were reported `MAPPED`; report `#24` and Municipal
> Corporation `#5` were verified with ULB code `248325`, while the corporation's
> legacy city remains `Mohali`; its phone and email are populated. `MAPPED` is
> an operator reconciliation result — the checked-in audit-table schema has no
> status column, and the migration runner only inserts unresolved worklist rows.

> **Implemented and locally validated; deployment/live verification pending.**
> Exact ULB-code municipal-contact lookup, preserved legacy-city compatibility,
> no city fallback after an exact ULB miss, partial-contact rendering and the
> related security rules are present in current source. Local validation records
> **316 backend tests passing**, frontend lint with **0 errors / 6 React Hook
> Form warnings**, and a passing frontend production build. These results do not
> prove that the municipal-contact fix is deployed or working live.

## What is stored

| Table | Columns | Meaning |
|---|---|---|
| `users` | `state`, `city` (ULB name), **`district`**, **`ulb_code`** | The account's registered place. `district`/`ulb_code` are NULL only on accounts created before the refactor, until reconciled. |
| `garbage_reports` | `state`, `city`, **`district`**, **`ulb_code`** | Code-backed reports (directory cascade) store the directory's canonical names plus the code; legacy reports keep their free-text values untouched. |
| `municipal_corporations` | `city`, **`ulb_code`** (unique) | `city` is the service-area display name, preserved verbatim; `ulb_code` is what code-backed reports route by. Mapping an office to a ULB never rewrites its `city`. |

## Routing, eligibility and leaderboards

- **Municipal routing is per report.** A report carrying a ULB code routes by
  that code and only by that code - the destination is resolved before any
  expensive work, and a miss is a loud 404 that names the municipal area
  ("not onboarded yet"), never a reroute to another corporation and never a
  city-alias guess. A legacy report without a code resolves its destination
  from the stored city, case-insensitively, exactly as it always has.
- **Cleaner eligibility is district-wide and fail-closed.** A cleaner may work
  on eligible reports in any ULB inside their registered district, and never
  outside it. A missing district on either the account or the report means
  the pair is simply not eligible - there is no city-string fallback. The
  rule lives in `CleanerDistrictEligibility` and is checked for open/nearby
  listings, proposal submission/update, start, adding activity and completion
  proof. Mutating paths check ownership where applicable before side effects.
  Own assignment/proposal reads, proposal withdrawal and activity deletion do
  not all recheck district eligibility; their ownership/state rules still apply.
- **Leaderboards have four scopes.** National, state and city keep their
  exact existing behavior; the district board (`GET
  /api/leaderboard/district/{state}/{district}`) aggregates every ULB inside
  one district and is keyed on the (state, district) pair, so identically
  named districts in different states never merge. The city endpoint is
  retained until a documented cleanup confirms no client still calls it.

## Migration (operator-runbook, T0–T5)

The following remains the safe, reusable rollout procedure. For the currently
recorded production environment it is historical: the operator-verified status
above says T0–T5 are complete, so **do not rerun the one-off backfill**.

The **location-V2 operator DDL and one-off data backfill** never run during
ordinary application startup. This statement is intentionally narrow: the
application still has unrelated, idempotent schema-maintenance initializers
for its existing columns and constraints. The location sequence is controlled
explicitly by the operator:

1. **T0 - build only.** The new artifact is built, nothing is deployed.
2. **T1 - migrate.** Backup → read-only preflight audits → run
   `db/add-location-columns.sql` (additive) → boot the new artifact ONCE with
   `cleanbharat.db.location-migration.enabled=true`. The runner backfills
   **`users` and `garbage_reports` only** (both carry a verifiable state) and
   writes every row it cannot map - ambiguous or unmatched - to the
   `location_backfill_audit` table. It never writes
   `municipal_corporations.ulb_code`: that table has no state column, so a
   city-name match alone could cross states. Corporations are mapped at T3.
3. **T2 - deploy with the flag off.** The old report form keeps working and
   behavior is identical to before; the new cascade form and the district
   leaderboard tab ship dark behind `VITE_LOCATION_V2_ENABLED=false`.
4. **T3 - map the corporations.** Every existing Municipal Corporation is
   mapped to its ULB through the admin cascade or a reviewed UPDATE - its
   stored `city` is preserved untouched. Preflight checks: unique ULB codes
   per corporation, and every state+city among the last 30 days of reports
   resolves to a mapped corporation. This preflight covers the explicitly
   audited locations only, not all 5,059 directory ULBs.
5. **T4 - cutover.** Flip `VITE_LOCATION_V2_ENABLED=true` (frontend redeploy).
   New reports are code-backed and route by ULB code; a valid but
   not-yet-onboarded ULB is a clean, communicated refusal.
6. **T5 - reconcile.** Work through `location_backfill_audit`, mapping each
   legacy row against the LGD workbook with targeted verified UPDATEs.
   Unmapped rows stay NULL, remain ineligible for district-restricted work,
   and are never authorized by a fallback.

**Rollback.** Flipping the frontend flag off and/or redeploying the previous
backend artifact never drops the new columns - every written mapping is
preserved. The schema rollback script (`db/rollback-location-columns.sql`) may
drop them only before any meaningful writes (genuinely loss-free) or, after
mappings exist, only with a fresh dedicated backup and explicit approval -
dropping populated columns is destructive and is never "data-loss-free".

---

# ☁️ Cloudinary Integration

Instead of storing image files inside the server or database, Clean Bharat stores images securely in **Cloudinary**.

Workflow

```text
Multipart Image
      │
      ▼
Cloudinary
      │
      ▼
Secure URL
      │
      ▼
Database
```

Advantages

- Faster image delivery
- Reduced backend storage
- Secure CDN URLs
- Automatic image optimization
- Easy image deletion

Both

- Garbage Images

and

- Cleanup Images

are stored using Cloudinary.

---

# 📍 Smart Location Module

A report combines live site evidence with a structured municipal jurisdiction.

Supported fields

- Latitude and longitude (fresh device GPS)
- Address and landmark
- State
- District
- City / municipal-area (ULB) name
- ULB code
- Pincode

Directory-backed reports carry `stateCode`, `districtCode` and `ulbCode`; the
backend validates the triple and derives the canonical names. The municipal
destination is resolved by exact ULB code **before** AI validation, duplicate
checking or Cloudinary upload. Reports filed through the legacy compatibility
shape retain their typed city/state and route by city only when `ulb_code` is
NULL.

Benefits

- Authoritative State → District → ULB identity instead of arbitrary city text
- Accurate GPS and duplicate detection (the existing pincode/time/bounding-box/
  Haversine pipeline is unchanged)
- District-wide cleaner eligibility across every ULB in the district
- Exact municipal routing without aliases or silent fallback
- Explicit, actionable refusal for a valid ULB not yet onboarded
- Future GIS and map integration

---

# 🏛 Municipal Corporation Module

Municipal Corporations are admin-managed organizations, separate from the
official directory: a valid ULB entry does **not** automatically create or
onboard a corporation.

Administrators manage:

- Organization Name
- Official ULB mapping (`ulb_code`)
- Preserved legacy city/service-area display name
- Phone Number
- Email

Creating a new corporation through the directory cascade stores the canonical
ULB name and code. Mapping an existing corporation sets its ULB code but never
rewrites its stored `city`, so old reports with `ulb_code = NULL` still reach
the same legacy destination.

Routing and contact lookup are deliberately split by report type:

| Report | Assignment/contact lookup | On no match |
|---|---|---|
| Directory-backed (`ulb_code` present) | Exact ULB code only; assignment uses `findByUlbCode`, contact uses authenticated `GET /api/municipal-corporations/ulb/{ulbCode}` | Explicit `404`; no city fallback, alias or substitute corporation |
| Legacy (`ulb_code` NULL) | Case-insensitive stored city; authenticated contact endpoint `GET /api/municipal-corporations/city/{city}` | Existing legacy `404` behavior |

`MunicipalCorporationResponse` exposes `id`, `city`, `ulbCode`,
`organizationName`, `phone`, `email` and nullable `mappedLocation` (state,
district, ULB name and type); passwords and hashes are never mapped.
Phone and email may be absent on historical response rows — one missing field does not hide
the organization or the other available contact. Current create/update request
validation requires contact fields.

Benefits

- Exact accountability for code-backed reports
- Backward-compatible routing for historical reports
- Preserved admin-managed contacts and login identities
- No code change required to onboard another verified ULB

## Municipal Corporation Login

Directory-backed creation enforces one Municipal Corporation per ULB code.
Legacy city labels are not database-unique. Municipal authority comes from the
registered corporation email and the assignment relationship, not a city supplied
by the client.

| Rule | Behaviour |
|------|-----------|
| Account creation | Only an Admin can register a corporation (directory triple or legacy city, organization, phone, email) |
| Login identity | The corporation's registered email — nothing else resolves to municipal authority |
| Default password | A shared bootstrap default defined in the service is BCrypt-hashed at creation and handed over offline; it is never returned by the API. Changing it after first login is expected, not enforced |
| Password change | `PATCH /api/account/password` works for corporations exactly as it does for users |
| Self-registration | Blocked — `/api/auth/register` rejects `ROLE_MUNICIPAL_OFFICER` and rejects any email already used by a corporation |
| Data scope | Municipal cleanup-console reads/decisions resolve the corporation from the authenticated email and check assignment ownership |

```text
Admin adds Municipal Corporation (ULB mapping or legacy city + email)
              │
              ▼
Default password stored as a BCrypt hash
              │
              ▼
Corporation signs in with that email
              │
              ▼
JWT issued with ROLE_MUNICIPAL_OFFICER
              │
              ▼
Municipal Console scoped to its assigned cleanups
```

---

## Account Profile API

Authenticated user accounts manage their own profile through `/api/account`; no endpoint accepts another user's ID.

| Method | Endpoint | Behaviour |
|--------|----------|-----------|
| `GET` | `/api/account/profile` | Returns the signed-in user's name, email, role, and optional profile-picture URL. |
| `PUT` | `/api/account/profile` | Updates an optional name and/or uploaded Cloudinary profile-picture URL. Old-avatar cleanup normally runs after transaction commit, with immediate cleanup only when transaction synchronization is inactive; Cloudinary itself is not a database-transaction participant. |
| `POST` | `/api/account/profile-picture` | Accepts a multipart `file`, uploads it to Cloudinary, and returns its secure URL for the profile update request. |
| `DELETE` | `/api/account/profile-picture` | Deletes the current avatar from Cloudinary and clears the stored profile-picture URL. |
| `GET` | `/api/account/record` | Returns the signed-in user's own identity and platform activity record. |
| `PATCH` | `/api/account/password` | Changes the authenticated account password after validating `currentPassword`, `newPassword`, and `confirmPassword`; this also supports municipal-corporation accounts. |
| `POST` | `/api/account/password/recovery/otp` | Sends a recovery code to the signed-in account's email when the current password is forgotten. |
| `POST` | `/api/account/password/recovery/verify` | Checks the code and returns a short-lived recovery token. |
| `POST` | `/api/account/password/recovery/confirm` | Consumes the recovery token to reset that same account's password. |

Recovery is **authenticated**, not a logged-out forgot-password flow. Codes use
Redis hashing, a ten-minute TTL, five guesses and a sixty-second resend cooldown.
The reset token is generated from 32 random bytes, hashed, expires after ten
minutes, is atomically single-use and is bound to the authenticated user or
corporation. Officers also have `GET` / `PUT /api/municipal-corporations/me` for
their own corporation profile/contact fields; `/api/account/profile` serves User
accounts.

---

# ⭐ Community Voting Module

Citizens collectively determine the urgency of garbage reports.

Every citizen can submit a rating from

⭐ 1 → 5

The backend automatically calculates

Average Urgency Score

Features

- One vote per citizen
- Vote updates supported
- Automatic average calculation
- Role-based authorization

Database Constraint

```text
UNIQUE

(User, Report)
```

This prevents duplicate votes while allowing vote updates.

---

# 💬 Community Discussion Module

The platform supports a fully threaded discussion system.

Implemented using a **self-referencing Comment entity**.

Features

- Unlimited nested replies
- Ownership validation
- Cascade deletion
- Recursive DTO responses

Discussion Tree

```text
Comment

├── Reply

│      ├── Reply

│      │      ├── Reply

│      │      └── Reply

│      └── Reply

└── Reply
```

Permissions

Citizen

- Comment
- Reply
- Delete Own

Cleaner

- Comment
- Reply
- Delete Own

Admin

- Comment
- Reply
- Delete Any Comment

---

# 🗄 Database Design

The backend targets **PostgreSQL** in production; H2 is a test-scoped dependency
used by repository slices, not a proven override for every test context. The
actual protected database/JPA configuration was not inspected in this audit.
Operator-run SQL handles the additive location-V2 columns. Tables 1–11 below map one-to-one to a
class in `com.cleanbharat.wastemanagement.entity`; `location_backfill_audit` is an operational SQL
audit table with no JPA entity.

## Tables at a Glance

| # | Table | Entity | Purpose |
|---|-------|--------|---------|
| 1 | `users` | `User` | Citizen, cleaner and admin accounts; municipal login accounts live in `municipal_corporations`; directory-backed rows include district and ULB code |
| 2 | `garbage_reports` | `GarbageReport` | Citizen-submitted complaints with GPS and either structured directory location or legacy city/state |
| 3 | `municipal_corporations` | `MunicipalCorporation` | Admin-managed civic body, login owner and optional exact ULB mapping |
| 4 | `cleanup_assignments` | `CleanupAssignment` | Work order raised for a report, owned by one assigned corporation |
| 5 | `cleanup_proposals` | `CleanupProposal` | Cleaner's site inspection + cleanup plan for an assignment |
| 6 | `cleanup_approvals` | `CleanupApproval` | Municipal decision trail — proposal stage **and** completion stage |
| 7 | `cleanup_activity_logs` | `CleanupActivityLog` | Optional progress notes posted while cleaning is in progress |
| 8 | `votes` | `Vote` | One urgency up-vote per user per report |
| 9 | `comments` | `Comment` | Threaded discussion on a report |
| 10 | `reward_history` | `RewardHistory` | Points ledger for cleaners |
| 11 | `public_feed_analytics` | `PublicFeedAnalytics` | Likes / views of a completed cleanup story |
| — | `location_backfill_audit` | *(no JPA entity)* | Operator reconciliation worklist for legacy user/report rows the one-off runner could not map safely |

## ER Diagram — Accounts & Community

```text
                 ┌──────────────────────────────┐
                 │            users             │
                 │──────────────────────────────│
                 │ id (PK)                      │
                 │ name                         │
                 │ email (unique)               │
                 │ password (BCrypt)            │
                 │ role / cleaner_type  <enum>  │
                 │ state / city (ULB name)      │
                 │ district (nullable)          │
                 │ ulb_code (nullable)          │
                 │ reward_points / created_at   │
                 │ google_subject / profile_pic │
                 └──────────────┬───────────────┘
                                │ 1:N  creates
                                ▼
                 ┌──────────────────────────────┐
                 │       garbage_reports        │
                 │──────────────────────────────│
                 │ id (PK)                      │
                 │ user_id (FK → users)         │
                 │ title / description          │
                 │ image_url                    │
                 │ latitude / longitude         │
                 │ address / landmark / pincode │
                 │ state / city (ULB name)      │
                 │ district / ulb_code (null on │
                 │   unreconciled legacy rows)  │
                 │ status / urgency_score       │
                 │ created_at                   │
                 └───┬──────────────────────┬───┘
              1:N    │                      │    1:N
        ┌────────────┘                      └────────────┐
        ▼                                                ▼
┌──────────────────────────────┐   ┌──────────────────────────────┐
│            votes             │   │           comments           │
│──────────────────────────────│   │──────────────────────────────│
│ id (PK)                      │   │ id (PK)                      │
│ user_id (FK)                 │   │ user_id (FK)                 │
│ report_id (FK)               │   │ report_id (FK)               │
│ created_at                   │   │ parent_comment_id (FK, self, │
│ UNIQUE (user_id, report_id)  │   │   nullable)                  │
└──────────────────────────────┘   │ content / created_at         │
                                   └──────────────────────────────┘
```

## ER Diagram — Cleanup Workflow

```text
   ┌──────────────────────────────┐      ┌──────────────────────────────┐
   │       garbage_reports        │      │    municipal_corporations    │
   │──────────────────────────────│      │──────────────────────────────│
   │ the citizen complaint        │      │ id (PK)                      │
   │ (see diagram above)          │      │ city (legacy/display value)  │
   │                              │      │ ulb_code (unique, nullable)  │
   │                              │      │ organization_name            │
   │                              │      │ phone / email                │
   └──────────────┬───────────────┘      └──────────────┬───────────────┘
                  │ 1:N work-order relationship         │ 1:N assigned jobs
                  └───────────────┬─────────────────────┘
                                  ▼
              ┌──────────────────────────────────────────────┐
              │             cleanup_assignments              │
              │──────────────────────────────────────────────│
              │ id (PK)                                      │
              │ report_id (FK)                               │
              │ municipal_corporation_id (FK)                │
              │ cleaner_id (FK → users, NULLABLE)            │
              │ status <enum>                                │
              │ cleanup_image_url                            │
              │ claimed_at / started_at / completed_at       │
              │ start_lat / start_lng / start_distance_m     │
              │ ai_verified / ai_confidence / ai_remarks     │
              └──┬───────────────────────────────────────────┘
                 │
                 ├── 1:N ──▶ cleanup_proposals      (cleaner bids — 1 per cleaner)
                 ├── 1:N ──▶ cleanup_approvals      (decisions of both stages)
                 ├── 1:N ──▶ cleanup_activity_logs  (optional progress notes)
                 ├── 1:N ──▶ reward_history         (points credited on completion)
                 └── 1:1 ──▶ public_feed_analytics  (likes & views of the story)
```

## Foreign Key Map

| Child table | Column | Parent | Nullable | Notes |
|-------------|--------|--------|----------|-------|
| `garbage_reports` | `user_id` | `users` | **Yes** in JPA | Reporting citizen is set by report creation |
| `cleanup_assignments` | `report_id` | `garbage_reports` | No | Service creates a work order; JPA mapping is many-to-one, not a unique report FK |
| `cleanup_assignments` | `municipal_corporation_id` | `municipal_corporations` | No | Corporation assigned to the report (exact ULB destination for code-backed reports) |
| `cleanup_assignments` | `cleaner_id` | `users` | **Yes** | Empty until a proposal is approved |
| `cleanup_proposals` | `assignment_id` | `cleanup_assignments` | No | Cascade ALL + orphan removal from parent |
| `cleanup_proposals` | `cleaner_id` | `users` | No | Unique with `assignment_id` |
| `cleanup_approvals` | `assignment_id` | `cleanup_assignments` | No | Assignment being reviewed |
| `cleanup_approvals` | `proposal_id` | `cleanup_proposals` | **Yes** | Set for `PROPOSAL` stage, `NULL` for `COMPLETION` |
| `cleanup_approvals` | `municipal_corporation_id` | `municipal_corporations` | No | Reviewing city body |
| `cleanup_approvals` | `decided_by` | `users` | **Yes** | Current corporation-account decisions leave this unset; corporation identity is recorded separately |
| `cleanup_activity_logs` | `assignment_id` | `cleanup_assignments` | No | Cascade ALL + orphan removal from parent |
| `cleanup_activity_logs` | `cleaner_id` | `users` | No | Author of the progress note |
| `votes` | `user_id`, `report_id` | `users`, `garbage_reports` | No | Unique pair — one vote per user per report |
| `comments` | `user_id`, `report_id` | `users`, `garbage_reports` | No | Discussion author + target report |
| `comments` | `parent_comment_id` | `comments` | **Yes** | Self reference → threaded replies |
| `reward_history` | `cleaner_id` | `users` | No | Cleaner who earned the points |
| `reward_history` | `assignment_id` | `cleanup_assignments` | No | Cleanup the points were credited for |
| `public_feed_analytics` | `cleanup_assignment_id` | `cleanup_assignments` | No | `@OneToOne` + unique — one row per completed cleanup |

## Table Notes

- **`users`** — the role enum supports four roles; normal municipal accounts are separate corporation
  rows. `cleaner_type` is filled only for cleaners. Directory-backed accounts store canonical
  `state`, ULB name in `city`, nullable `district` and nullable `ulb_code`. The operator location SQL
  creates the `(state, district)` index; it is not declared on the User entity. A cleaner whose district
  has not been reconciled is not eligible for district-restricted work.
- **`garbage_reports`** — stores GPS/address plus `state`, ULB name in `city`, nullable `district` and
  nullable `ulb_code`. Code-backed reports fill all four directory fields; legacy reports preserve
  their typed state/city and keep the new fields NULL until reconciled. `(state, district)` is indexed;
  the duplicate detector's pincode/location/created-at indexes and algorithm are unchanged.
- **`municipal_corporations`** — admin-managed master data (preserved city/display value, nullable
  unique `ulb_code`, organisation name, phone, email and password hash). It is both the contact
  directory and assignment owner. Mapping an existing row sets only `ulb_code`; it never rewrites
  the legacy `city` that code-less reports still use.
- **`location_backfill_audit`** — operator-created SQL audit table, not a JPA entity. The explicitly
  enabled `LocationMigrationRunner` examines `users` and `garbage_reports` only, writes unresolved
  rows to this worklist, and never auto-maps corporations (they have no persisted state with which to
  disambiguate a city name).
- **`cleanup_assignments`** — created automatically when a report is accepted, so `cleaner_id` stays
  `NULL` while the job is still open for bids. The GPS columns store where the cleaner actually stood
  when starting work (`start_distance_m` is the distance from the reported spot).
- **`cleanup_proposals`** — carries proof of inspection (`inspection_image_url`, `inspection_latitude`,
  `inspection_longitude`, `inspection_distance_meters`, `inspected_at`, `site_observations`) and the
  plan itself (`estimated_duration_days`, `manpower_count`, `equipment`, `cleaning_method`,
  `waste_handling_plan`, `estimated_waste_volume`, `proposed_start_date`, `remarks`).
  A unique constraint `uk_proposal_assignment_cleaner (assignment_id, cleaner_id)` allows **many
  cleaners per assignment but only one live proposal per cleaner** — a revision updates the same row.
- **`cleanup_approvals`** — a single reusable audit table for **both** review gates; `stage` says which
  gate it belongs to, `decision` says the outcome, `municipal_corporation_id` records the corporation,
  and nullable `decided_by` is not filled by current corporation-account decisions.
  `decided_at` is stamped once (decisions are never edited, only new rows are added).
- **`cleanup_activity_logs`** — optional notes (`description`, `activity_at`, optional `image_url` and
  GPS) that a cleaner may post while the assignment is `IN_PROGRESS` or `REWORK_REQUIRED`.
  Description is required (maximum 1,000 characters); image and GPS are optional and are not AI-verified.
- **`reward_history`** — points ledger; crediting also increments `users.reward_points` (`User.rewardPoints`).
  Reads do not recompute that stored total from the ledger, and the assignment FK has no unique constraint.
- **`public_feed_analytics`** — engagement counters for the public success-story feed, one row per
  completed assignment.

## Persisted Enum Columns

All enums are stored as text via `@Enumerated(EnumType.STRING)`. Exactly seven columns are enum-backed:

| Column | Enum | Values |
|--------|------|--------|
| `users.role` | `Role` | `ROLE_ADMIN`, `ROLE_CITIZEN`, `ROLE_CLEANER`, `ROLE_MUNICIPAL_OFFICER` |
| `users.cleaner_type` | `CleanerType` | `MUNICIPAL`, `INDIVIDUAL`, `NGO`, `PRIVATE` (nullable) |
| `garbage_reports.status` | `ReportStatus` | `PENDING`, `IN_PROGRESS`, `RESOLVED` |
| `cleanup_assignments.status` | `AssignmentStatus` | `PENDING`, `PROPOSAL_SUBMITTED`, `ASSIGNED`, `IN_PROGRESS`, `AWAITING_APPROVAL`, `REWORK_REQUIRED`, `CLAIMED` *(legacy)*, `COMPLETED` |
| `cleanup_proposals.status` | `ProposalStatus` | `SUBMITTED`, `APPROVED`, `REJECTED`, `REVISION_REQUIRED`, `WITHDRAWN` |
| `cleanup_approvals.stage` | `ApprovalStage` | `PROPOSAL`, `COMPLETION` |
| `cleanup_approvals.decision` | `ApprovalDecision` | `APPROVED`, `REJECTED`, `REVISION_REQUIRED`, `REVISION_SUBMITTED` (system history entry on proposal revision) |

`CLAIMED` is kept only so historical rows from the older direct-claim flow still load; new assignments
never enter that state.

Enums that are **never** stored in the database (runtime / API only):

| Enum | Where it lives |
|------|----------------|
| `BadgeType` (`BRONZE`, `SILVER`, `GOLD`) | Computed on the fly in `LeaderboardServiceImpl.calculateBadge()` from `users.reward_points` |
| `ImageRejectionReason` (`NO_GARBAGE`, `INSIGNIFICANT_GARBAGE`, `NOT_REAL_IMAGE`, `POOR_QUALITY`, `IRRELEVANT_SUBJECT`, `UNCERTAIN`) | AI validation responses and `InvalidReportImageException` only |
| `LeaderboardType` (`NATIONAL`, `STATE`, `DISTRICT`, `CITY`) | Leaderboard scope in response DTOs |
| `GarbageSeverity` (`LOW`, `MEDIUM`, `HIGH`) | AI report-validation output |

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Enum as STRING** | Human-readable rows and no ordinal corruption when a new enum value is added |
| **Nullable `cleaner_id`** | An assignment exists before anyone is selected, so the winner is stamped only at approval time |
| **One approvals table, two stages** | Proposal review and completion review share the same audit shape, so `stage` avoids a duplicate table |
| **Unique `(assignment_id, cleaner_id)`** | Competitive bidding without duplicate proposals from the same cleaner |
| **Unique `(user_id, report_id)` on votes** | Prevents double voting on urgency |
| **Self-referencing `comments`** | Threaded replies without a separate replies table |
| **Append-only ledger + cached points** | `reward_history` keeps the audit trail while `users.reward_points` keeps leaderboard queries fast |
| **Assigned-corporation scoping** | `municipal_corporation_id` on assignments/approvals keeps every officer inside their own corporation's data; exact ULB routing decides that corporation for code-backed reports |

## Schema Maintenance

When configured as `spring.jpa.hibernate.ddl-auto=update`, Hibernate schema update
is not a complete migration strategy. Existing widths, nullability, unique rules
and enum `CHECK` constraints need explicit review and targeted maintenance.

Four PostgreSQL schema-maintenance runners handle these gaps and log warnings
rather than failing startup. They can run repeatedly; enum constraint repair
still drops/recreates relevant checks on later boots rather than becoming a
strict no-op. Related hand-runnable SQL is in `src/main/resources/db/`; scripts
are not always exact twins (the stale-constraint script also adds a proposal FK
and index). These runners are unrelated to the location-V2 data backfill in the
**Location Model** section above.

| Runner | Repairs | Manual script | Switch off with |
|--------|---------|---------------|-----------------|
| `ColumnWidthInitializer` | Text columns narrower than the entity declares — `garbage_reports.description` → 500, `comments.message` → 1000. Widen-only, so no value can stop fitting | `db/widen-text-columns.sql` | `cleanbharat.db.widen-text-columns=false` |
| `ColumnNullabilityInitializer` | `NOT NULL` left on a column the entity now allows to be null — `cleanup_approvals.decided_by`, since a corporation decides under its own account | `db/relax-optional-columns.sql` | `cleanbharat.db.relax-optional-columns=false` |
| `StaleUniqueConstraintInitializer` | Old `UNIQUE` rules on append-only ledgers — `cleanup_approvals` collects one row per municipal decision | `db/drop-stale-unique-constraints.sql` | `cleanbharat.db.drop-stale-unique-constraints=false` |
| `EnumCheckConstraintInitializer` | `*_check` constraints that predate newer enum values, most importantly `cleanup_assignments.status` | `db/fix-enum-check-constraints.sql` | `cleanbharat.db.repair-enum-constraints=false` |

Why the column width one exists: `garbage_reports.description` was created as `varchar(255)` before
`CreateReportRequest` allowed 500 characters, so a long description failed with SQLSTATE `22001` and the
citizen was shown the duplicate-report wording for a report that was not a duplicate. Widening a
`varchar` in PostgreSQL is a catalogue-only change — no table rewrite, no data touched. A column already
changed to unbounded `text` is left alone rather than narrowed.

`MunicipalPasswordInitializer` also runs on ordinary startup. It fills missing
or blank corporation password hashes with the shared bootstrap default, without
resetting a corporation's existing chosen password. It is separate from the
PostgreSQL schema repairs and the operator-gated location migration.

`db/backfill-report-status-in-progress.sql` is a one-time data fix, not a schema repair, and has no
runner — it is meant to be executed by hand.

`LocationMigrationRunner` is different again: it is **absent from ordinary
startup** unless the operator explicitly supplies
`cleanbharat.db.location-migration.enabled=true`. It first verifies that
`db/add-location-columns.sql` has already created the five columns and audit
table, then maps only `users` and `garbage_reports` within each row's verified
state. It never writes `municipal_corporations.ulb_code`; corporations are
mapped through the verified admin cascade or an operator-reviewed T3 update.
The flag is for one intentional boot only and must remain absent afterward.

---

# 🔒 Security Architecture

Security is implemented using **Spring Security** with JWT authentication.

Main Components

- SecurityConfig
- JwtAuthenticationFilter
- RateLimitFilter
- JwtService
- CustomUserDetailsService
- EmailVerificationService + MailConfig (OTP ownership proof)
- GoogleIdentityService + GoogleAuthConfig (ID-token verification)

Request Flow

```text
Incoming Request

        │

Authorization Header

        │

Bearer Token

        │

JwtAuthenticationFilter

        │

Validate Token

        │

Load User Details

        │

SecurityContextHolder

        │

RateLimitFilter          ── over limit? → 429 + Retry-After (stops here)

        │

Controller
```

Security Features

- Stateless Sessions
- BCrypt Password Hashing
- JWT Expiration
- Role-Based Access
- Endpoint Protection
- Email-ownership proof (OTP code) before account creation
- Google ID-token verification (audience-pinned, no client secret)
- Distributed API Rate Limiting
- Global Exception Handling

---

# 🤖 Artificial Intelligence Integration

Artificial Intelligence is one of the defining features of Clean Bharat.

The project integrates **Google Gemini Vision AI** using **OpenFeign**.

Two independent AI pipelines have been implemented.

---

## 1️⃣ Garbage Report Validation

Before a report is accepted, Gemini verifies

- Garbage exists
- Image quality
- Garbage category
- Garbage severity
- AI-generated image detection
- Confidence score

Only reports that satisfy the configured confidence threshold are accepted.

---

## 2️⃣ Cleanup Verification

Before rewarding cleaners,

Gemini compares

Before Image

↓

After Image

and verifies

- Same location
- Similar surroundings
- Garbage removed
- Camera angle similarity
- Fake cleanup detection
- Confidence threshold

Only then does the cleanup enter `AWAITING_APPROVAL`. Municipal completion
approval is still required before `COMPLETED`, rewards and public publication.

---

# 🧠 Prompt Engineering

Instead of relying on free-form AI responses, the backend instructs Gemini using carefully designed prompts that enforce structured JSON output.

The prompts require Gemini to evaluate multiple conditions, including:

- Garbage detection
- Location consistency
- Camera perspective
- Image authenticity
- Cleanup completion
- Confidence score

This structured approach significantly improves response consistency and simplifies backend validation.

---

# 📍 Intelligent Duplicate Report Detection

One of the most unique engineering features of Clean Bharat is its optimized duplicate detection algorithm.

Instead of comparing every report with every other report, the backend performs duplicate detection through a multi-stage filtering pipeline.

```text
Step 1

Filter by Same Pincode

        │

Step 2

Recent Reports Only

(Configurable Days)

        │

Step 3

Bounding Box Filter

        │

Step 4

Haversine Distance

        │

Step 5

Duplicate Decision
```

This dramatically reduces unnecessary distance calculations and database scans.

If a duplicate report is detected,

the citizen is redirected to the existing report instead of creating another report.

Benefits

- Prevents database duplication
- Encourages community collaboration
- Reduces municipal workload
- Improves report prioritization
- Saves AI processing cost

This algorithm combines **geospatial filtering** with **distance-based validation**, making it both accurate and scalable for large datasets.

---

# ⚡ Redis Caching Architecture

The backend uses **Redis** (via Spring Data Redis + Spring Cache Abstraction) as a read-through cache for expensive, read-heavy aggregates. PostgreSQL remains the source of truth — Redis holds response DTOs to avoid repeated queries. No latency or memory-footprint benchmark is established by the repository.

```text
Client
  │
  ▼
@Service method (@Cacheable)
  │
  ├──► CACHE HIT  → Redis returns DTO (no SQL)
  │
  └──► CACHE MISS → PostgreSQL query → store DTO in Redis with TTL → return
```

Writes do not update cached DTOs directly; annotated paths **evict** them
(`@CacheEvict` / `@Caching`) so the next read recomputes. TTL bounds stale
responses for paths without eviction; it is not proof that every view is
immediately fresh. For example, proof upload evicts admin/municipal statistics,
not homepage impact, and new cleaner registration does not evict leaderboard
results. Unlisted caches use the manager's default **10-minute TTL**.

## Cached Views

| Cache | Key | TTL | Producer | Why cache this? |
|--------|-----|-----|----------|-----------------|
| `homepage_impact_stats` | `'platform-impact'` | 10 min | `AnalyticsServiceImpl.getPlatformImpact()` → `GET /api/analytics/platform-impact` (public) | The 4 homepage **Platform Impact** counters in one small JSON; read by every visitor, logged in or out |
| `leaderboard_top` | `'national'`, `'state:<name>'`, `'district:<state>\|<district>'`, `'city:<name>'` | 5 min | `LeaderboardServiceImpl` (national / state / district / city) | Top-10 sort + per-cleaner counts; district keys include state so identically named districts never share a board. City keys remain supported |
| `admin_dashboard_stats` | `'overview'` | 10 min | `AdminServiceImpl.getDashboard()` | 10 aggregate counts; identical for every admin, so one shared key is safe |
| `municipal_dashboard_stats` | signed-in corporation email (lowercased) | 5 min | `CleanupApprovalServiceImpl.getDashboardStats()` | 5 assignment-scoped counts; per-tenant key so one corporation can never read another's numbers |

Cached values contain counts and short response fields, not image binaries.
Public-feed responses (including Cloudinary URLs and viewer-specific `likedByMe`)
are deliberately not Spring-cached. The directory's separate one-day HTTP cache
and Redis OTP, recovery-token and rate-limit storage are not these view caches.





## Eviction Map (what invalidates what)

| Write event | Evicts | Reason |
|-------------|--------|--------|
| Report created | homepage, admin, municipal | New `PENDING` report moves all three overviews |
| Register / promote user / vote / comment / reply / delete comment | admin | User, vote and discussion totals shift |
| Proposal submitted / decided | municipal | Site moves between pending and active queues |
| Cleanup started / proof uploaded | admin + municipal | Report leaves `PENDING`; AI-verified count and queues move |
| Completion approved or sent for rework | homepage, leaderboard, admin, municipal | Both branches evict; only approval resolves the report and credits points, while rework retires the AI verdict |
| Reward credited | leaderboard, homepage, admin | Points reorder the rankings (defensive double-eviction with completion approval) |
| Assignment deleted | homepage, leaderboard, admin, municipal | Related counts, ranks and queues may change |
| Report deleted | admin + municipal; homepage/leaderboard additionally when its assignment is deleted | Dependent assignment deletion supplies the extra evictions |
| User deleted | admin + municipal, with related deletion-service evictions when reports/assignments are removed | Depends on owned data |
| Corporation deleted | municipal | Corporation register deletion does not evict every cache |

Proposal edits/withdrawals and corporation creation have no matching view-cache
eviction annotation; they must not be described as immediate invalidation paths.

```text
Cleanup lifecycle × cache (verified against the Complete Cleanup Lifecycle):

create → evicts homepage/admin/municipal
propose / decide → evicts municipal
start / upload → evicts admin/municipal
approve completion → evicts homepage + leaderboard + admin + municipal
```

## Configuration Notes

- `@EnableCaching` is declared on both `WastemanagementApplication` and `config/RedisConfig.java`; per-cache TTLs and JSON serialization (readable keys, Jackson DTO values, nulls never cached) live in the `RedisCacheManager` bean.
- Spring Boot configures Lettuce from Redis connection properties such as `spring.data.redis.url` / `SPRING_DATA_REDIS_URL` (`redis://` or `rediss://` for TLS), with Commons Pool2 available. Actual environment configuration and credential storage were not inspected.
- Covered by `RedisCacheSerializationTest` (DTO round-trip + municipal SpEL key isolation). `PlatformImpactResponse` uses the same Jackson-safe shape (`@NoArgsConstructor` + `@AllArgsConstructor`) so a cache HIT always deserializes.
- Supplementary design notes: `Clean_Bharat_Redis_Caching_Revision_Notes.pdf` is not tracked in this repository; a copy exists in the outer workspace's `Revision_Notes/`. Current source is authoritative.

---

# 🚦 API Rate Limiting

The same Redis instance also backs **distributed API rate limiting**, so one caller cannot flood the API or burn the paid Gemini / Cloudinary quota. The counter lives in Redis rather than in process memory, which means the limit is shared across **every application instance** — scaling to two containers does not silently double every allowance.

```text
Incoming Request
  │
  ▼
JwtAuthenticationFilter        → SecurityContext populated (when a valid JWT is present)
  │
  ▼
RateLimitFilter                → tier resolved from HTTP method + decoded path
  │                              identity = authenticated principal, else client IP
  │
  ├──► OVER LIMIT → 429 + Retry-After, request stops here
  │
  ▼  under limit → INCR in Redis, chain continues
Authorization → Controller → Service (Gemini / Cloudinary / PostgreSQL)
```

Placement is the whole design: the filter sits **after** `JwtAuthenticationFilter` so a signed-in caller is counted by principal instead of sharing an IP bucket, and **before** authorization, so an over-limit request is rejected while it is still cheap — no controller, no AI call, no upload.

## Tiers

| Scope | Limit / min | Routes | Counted by |
|-------|-------------|--------|------------|
| `auth` | 10 | `/api/auth/**`, `/api/account/password` and descendants | IP on JWT-bypassed auth routes; principal on authenticated password routes |
| `ai` | 5 | Report/file/proof uploads; `POST` activity logs; `POST`/`PUT` proposals; `/test/**` | Principal when resolved, otherwise IP |
| `community` | 30 | non-`GET` on `/api/votes`, `/api/comments/**`, `/api/public-feed/**` | Principal when resolved; IP for anonymous views/shares |
| `staff` | 60 | `/api/admin/**`, `/api/municipal-corporations` and descendants, `/api/cleanup-approvals/**` | Principal when resolved, otherwise IP |
| `pub` | 100 | Remaining requests, including public reads and authenticated account/assignment/reward paths | Principal when resolved, otherwise IP |

These are source defaults for a 60-second fixed window, not verified production
values. Tier names do not set access rules. Profile-picture upload currently
falls into `pub`, not `ai`. Public routes that bypass JWT processing remain
IP-keyed even if a caller attaches a token.

The `/api/health` path (all methods), paths beginning `/actuator`, and every `OPTIONS` pre-flight are **exempt**. The whole policy lives in one `resolve(method, path)` method in `config/RateLimitProperties.java` — no rate-limit logic is scattered across controllers.

## Key & Window

```text
rl:{scope}:{sha256_16(user:<principal> or ip:<address>)}:{window}
 │   │       │                     └── floor(epochSeconds / 60) — the key itself rotates
 │   │       └── first 16 hex of SHA-256, so no email is ever written to Redis in clear text
 │   └── auth | ai | community | staff | pub
 └── dedicated namespace — cannot collide with a `cacheName::key` cache entry
```

Counting is a single **atomic Lua script**: `INCR`, `EXPIRE` only when the counter reads `1`, then `TTL` in the same reply. Doing it as separate commands would leave a window where a crash between `INCR` and `EXPIRE` strands a key with no TTL — a caller locked out forever. Refreshing the TTL on every hit instead of only the first would turn a one-minute limit into a rolling lockout.

The expiry sent to Redis is the **seconds remaining in the current window**, not a full window, because the key rotates on absolute clock boundaries. A bucket first touched at `:55` of a 60-second window resets 5 seconds later, not 60 — so `Retry-After` is exact, and expired buckets disappear on their own with no cleanup job and no unbounded growth.

## Configuration Notes

- Registered once via `.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)` in `config/SecurityConfig.java`. A `FilterRegistrationBean` with `setEnabled(false)` suppresses Boot's auto-registration of the `@Component` filter, which would otherwise run it twice per request.
- Reuses the auto-configured `StringRedisTemplate` on the **same** `RedisConnectionFactory` as the cache — one connection to Redis, and cache TTL / eviction behaviour is untouched.
- The `429` is written directly by the filter using the project's existing `ErrorResponse` DTO. `@RestControllerAdvice` cannot shape a response produced inside a servlet filter, so routing it through `GlobalExceptionHandler` for symmetry would simply not work. `Retry-After` is CORS-exposed so the browser can read it.
- **Fails open.** If Redis is briefly unreachable the request is allowed rather than rejected — a limiter that takes the whole API down with it would be a bigger outage than the abuse it prevents.
- Anonymous callers are keyed by the client IP taken from `X-Forwarded-For` **counted from the right**, because the platform proxy appends the peer it actually observed; entries further left are caller-supplied and can say anything. `ratelimit.trusted-proxy-hops=0` ignores the header entirely for direct or local runs.
- Every number is a `@Value` tunable with an in-code default (`ratelimit.enabled`, `.window-seconds`, `.public-per-minute`, `.auth-per-minute`, `.expensive-per-minute`, `.community-per-minute`, `.staff-per-minute`, `.trusted-proxy-hops`), so the application runs unchanged with no new configuration and any limit can be retuned per environment.
- Covered by **37 unit tests** — `RateLimitServiceTest` (key shape, identity hashing, window-boundary expiry, fail-open) and `RateLimitFilterTest` (tier resolution, principal-vs-IP identity, proxy handling, 429 shape, single execution per request).
- Supplementary design notes: `Clean_Bharat_Rate_Limiting_Revision_Notes.pdf` is not tracked in this repository; a copy exists in the outer workspace's `Revision_Notes/`. Current source is authoritative.

---

# 🚀 Advanced Backend Features

Beyond core modules like authentication, reporting, AI validation, and community engagement, the backend includes several advanced systems that automate the complete waste management lifecycle. These modules work together to transform Clean Bharat from a simple reporting application into a **production-oriented smart waste management platform**.

---

# 🧹 Smart Cleanup Assignment Workflow

Once a garbage report successfully passes AI validation and duplicate detection, the platform automatically creates a cleanup assignment and hands it to the Municipal Corporation of that city.

Cleaners never take work on their own authority. A cleaner may only *propose* a cleanup; the city Municipal Corporation decides who executes it and whether the finished work is acceptable.

## Complete Cleanup Lifecycle

```text
Citizen Reports Garbage
          │
          ▼
AI Validates Report
          │
          ▼
Duplicate Detection
          │
          ▼
Report Created
          │
          ▼
Assignment Created for the City Municipal Corporation
          │
          ▼
Cleaner Submits Cleanup Proposal
          │
          ▼
Municipal Reviews the Proposal
          │
          ├──► REJECTED ──────► Site reopens for other cleaners
          │
          ├──► REVISION_REQUIRED ──► Cleaner resubmits the proposal
          │
          ▼
APPROVED → Cleaner Assigned
          │
          ▼
Cleaner Starts Cleanup (GPS verified within 50 m)
          │
          ▼
Optional Activity Log Updates
          │
          ▼
Cleaner Uploads Cleanup Proof (GPS + After Image)
          │
          ▼
Google Gemini AI Verification
          │
          ├──► AI Rejected ──► Cleaner stays on site and re-uploads
          │
          ▼
AWAITING_APPROVAL
          │
          ▼
Municipal Completion Review
          │
          ├──► Rework Requested ──► REWORK_REQUIRED
          │
          ▼
COMPLETED
          │
          ▼
Reward Generated (Repeat Check)
          │
          ▼
Report Marked as Resolved
          │
          ▼
Public Feed Updated
          │
          ▼
Leaderboard Updated
```

---

## Assignment States

Every cleanup assignment progresses through predefined states to ensure complete traceability.

| Status | Description |
|---------|-------------|
| **PENDING** | Assignment created for the city corporation, open for proposals |
| **PROPOSAL_SUBMITTED** | A cleaner has proposed a cleanup and is waiting for the municipal decision |
| **ASSIGNED** | Proposal approved — this cleaner is authorised to execute the cleanup |
| **CLAIMED** | Legacy state retained for older records so historical data stays readable |
| **IN_PROGRESS** | Cleanup work has started with verified on-site GPS |
| **REWORK_REQUIRED** | The municipality asked for the cleanup to be redone |
| **AWAITING_APPROVAL** | AI-verified proof submitted, waiting for the municipal completion sign-off |
| **COMPLETED** | Officially signed off by the Municipal Corporation |

Maintaining these states enables accurate progress tracking while preventing conflicting operations.

Guard rails enforced by the service layer:

- New `ASSIGNED` work cannot be started unless a matching `PROPOSAL` + `APPROVED` approval record exists; legacy `CLAIMED` records retain their compatibility path.
- Only the assigned cleaner can start work, log activity, or upload proof for that assignment.
- Start and proof uploads require finite device coordinates and reject distances beyond **50 metres** when the report has site coordinates. Legacy reports without coordinates bypass the distance comparison.
- Proof cannot be uploaded before the cleanup is started or after it is officially completed.
- Activity logs are **optional**, accepted in `IN_PROGRESS` or `REWORK_REQUIRED`; description is required, photo/GPS are optional and not AI-verified. Activity-file MIME/size validation is frontend-side; this backend path delegates nonempty files to Cloudinary without its own image validation.

Municipal rework keeps the assigned cleaner, sets `REWORK_REQUIRED`, clears the
completion timestamp and retires AI verification/confidence. New GPS + AI proof
must pass before another municipal review. The supplementary workflow PDF's
`IN_PROGRESS` rework wording differs from this current implementation.

---

## Cleaner Dashboard

Dedicated APIs provide cleaners with a personalized workspace.

Cleaners can:

- Browse Open Sites across all ULBs in their registered state/district and submit proposals
- Track Their Submitted Proposals and municipal decisions
- View Approved Work Awaiting Start
- View Assignments In Progress
- Add Optional Activity Log Updates
- Upload Cleanup Proof for Municipal Review
- View Completed Assignments and Rewards
- View Nearby Assignments *(prepared for future Google Maps integration)*

Role-based authorization ensures only authenticated cleaners can access these resources.

---

## Municipal Console

Each Municipal Corporation gets a dedicated workspace scoped strictly to the
assignments whose `assignedMunicipalCorporation` is that authenticated
corporation — not to a client-supplied city or district.

Corporations can:

- View their corporation-scoped dashboard statistics
- Review the proposal queue and approve, reject, or request revision
- Monitor active cleanups and their activity logs
- Review AI-verified completion proof and sign off or send back for rework
- Read the full approval history of any assignment assigned to them

Every request resolves the corporation from the signed-in email and then
checks the assignment relationship, so one corporation can never read or
decide another corporation's cleanups, even when legacy city labels and
canonical ULB names differ.

---

# 🏆 Reward System

The reward system motivates cleaner participation by recognizing officially verified cleanup efforts instead of merely tracking completed assignments.

Rewards are generated **only after the Municipal Corporation approves completion**.
The service checks for an existing assignment reward to suppress sequential
repeat crediting; this is not a database-enforced concurrency guarantee.

---

## Reward Generation

```text
Cleaner Uploads Proof (GPS verified)
        │
        ▼
Gemini AI Verification
        │
        ▼
AWAITING_APPROVAL
        │
        ▼
Municipal Completion Approval
        │
        ▼
Already Rewarded? ──► Yes ──► No second reward
        │
        No
        ▼
Reward Created (+50 Points)
        │
        ▼
Reward Points Added
        │
        ▼
Leaderboard Updated
```

The service uses `existsByAssignment` before adding **50 points** and a reward
row. `reward_history.assignment_id` is not unique and this path has no explicit
locking, so simultaneous approvals/rewards are not proven exactly-once by the
current code. Ordinary repeat completion decisions are also blocked once the
assignment is no longer `AWAITING_APPROVAL`.

---

## Reward History

Rather than storing only total points, the platform maintains a complete reward ledger.

Example

```text
Cleaner Rewards

+50   Report #21

+50   Report #25

+50   Report #29

+50   Report #32
```

Benefits include:

- Complete reward history
- Cleaner transparency
- Future incentive campaigns
- Auditable reward calculations

---

# 🥇 Live Leaderboard System

To recognize outstanding contributions, Clean Bharat implements a live leaderboard system without introducing an additional leaderboard table.

Rankings are computed from existing data **on a cache miss**, not on every
request. The four public boards use `leaderboard_top` with a **five-minute TTL**
and write-path evictions; personal national standing is not Spring-cached.
There is no dedicated leaderboard database table, but ranked response DTOs
are cached in Redis.

Reused entities include:

- User
- CleanupAssignment
- RewardHistory

---

## Leaderboard Levels

The platform supports multiple ranking scopes.

| Leaderboard | Description |
|-------------|-------------|
| 🇮🇳 National | Top cleaners across the country |
| 🏙 State | Top cleaners within a state |
| 🗺 District | Top cleaners across every ULB in one `(state, district)` pair; identical district names in different states stay separate |
| 🌆 City | Uniquely resolved official ULB name uses ULB identity; ambiguous/unknown names or unavailable directory use case-insensitive legacy city matching, never District fallback |
| 👤 Personal | Authenticated cleaner's national standing, including positions beyond the public Top 10 |

---

## Badge System

Cleaners automatically receive achievement badges based on accumulated reward points.

| Points | Badge |
|---------|-------|
| 0 – 199 | 🥉 Bronze |
| 200 – 499 | 🥈 Silver |
| 500+ | 🥇 Gold |

---

## Sequential Ranking

The backend assigns **sequential board positions**. National ties are ordered
by cleaner ID ascending after reward points descending; personal standing counts
higher-point cleaners plus equal-point cleaners with smaller IDs. The paired
30 October 2026 implementation changes and regression tests explicitly align
personal standing with the national board.

Example

```text
Reward Points

300
200
200
150

Ranks

1
2
3
4
```

State, district and city boards also number rows sequentially, but their current
repository queries do not specify an ID tie-break. Equal-point ordering in those
scopes is therefore not guaranteed. Some frontend competition-ranking copy is
stale; it does not describe the current ranking contract.

---

# 📊 Analytics Engine

Community participation is one of the strongest indicators of report importance.

To prioritize reports intelligently, Clean Bharat continuously calculates engagement statistics using community activity.

---

## Engagement Score

The platform combines voting and discussion activity into a single score.

```text
Urgency Score

+

(Comment Count × 2)

+

(Reply Count × 1)

=

Engagement Score
```

The engagement score is automatically recalculated whenever:

- A vote is submitted
- A vote is updated
- A comment is added
- A reply is added
- A comment is deleted
- Nested replies are removed

---

## Dashboard Analytics

The Analytics Module provides platform-wide statistics.

Examples include:

- Total Reports
- Total Users
- Total Votes
- Total Comments
- Total Replies
- Average Urgency Score
- Average Engagement Score
- Most Trending Report

---

## Trending Reports

Reports are ranked dynamically according to engagement.

To improve scalability, the backend avoids the classic **N+1 Query Problem** by grouping discussion data in memory using Java Streams instead of repeatedly querying the database.

Benefits:

- Reduced database calls
- Faster response times
- Better scalability
- Cleaner service logic

---

# 🌐 Public Feed Architecture

Transparency is one of the primary goals of Clean Bharat.

Instead of hiding successful cleanup operations inside dashboards, the platform publishes officially completed cleanups to a dedicated public feed.

A cleanup that is only AI-verified is **not** published. Publication happens after the Municipal Corporation signs off, so cleanups sent back for rework never appear as success stories.

The feed remains readable **without authentication**, allowing anyone to explore successful sanitation efforts. Feed queries select `COMPLETED` assignments with `aiVerified=true`; they do not independently check approval history. Viewing/sharing is public; appreciation requires an authenticated User account (one like per account), and municipal corporation accounts are not User rows.

---

## Community Success Flow

```text
Cleaner Uploads Proof
        │
        ▼
Gemini Verification
        │
        ▼
Municipal Completion Approval
        │
        ▼
Reward Generated
        │
        ▼
Completed Assignment Eligible for Feed Query
(analytics row initialized; no separate feed entity)
        │
        ▼
Available for Everyone
```

Each feed item includes:

- Before Image
- After Image
- Cleaner Information
- Municipal Corporation
- Cleanup Details
- View Count
- Like Count
- Share Count

---

## Community Awareness vs Community Success

To keep responsibilities clearly separated, the homepage is divided into two independent sections.

### Community Awareness

Displays reports that still require attention.

Uses:

- Trending Reports
- Engagement Analytics
- Urgency Score

---

### Community Success

Displays successfully completed cleanups.

Uses:

- Public Feed
- AI Verification
- Community Appreciation

This separation makes both modules reusable while keeping the backend architecture clean.

---

# 👨‍💼 Admin Portal

The Admin Portal acts as the central management system for the entire platform.

It enables administrators to monitor platform activity while maintaining strict business rules and secure management operations.

---

## Dashboard

The dashboard provides real-time statistics including:

- Total Users
- Citizens
- Cleaners
- Admins
- Garbage Reports
- Pending Reports
- Completed Reports
- AI Verified Cleanups
- Total Votes
- Total Comments
- Top Performing Cleaner

---

## User Management

Administrators can:

- View all users
- Search by name
- Search by email
- Filter by role
- Promote Citizen → Admin
- Delete Citizens
- Delete Cleaners *(business validation applied)*

Business rules ensure:

- Admin accounts cannot be deleted.
- Cleaners with any assigned work orders cannot be removed.
- Only Citizens can be promoted to Admin.

---

## Report Management

The portal also supports powerful report management.

Administrators can:

- Search reports
- Filter reports
- Delete reports safely

Deleting a report automatically performs complete cleanup of associated resources including:

- Cloudinary Images
- Votes
- Comments
- Cleanup Assignments
- Reward History
- Public Feed Analytics

The service coordinates dependent database deletion and external image cleanup.
Database transactions do not make Cloudinary calls atomic, so external cleanup
must not be described as a guaranteed rollback-safe operation.

---

# ⚙️ Production-Level Optimizations

Throughout development, the backend was continuously refined to follow production-oriented engineering practices rather than focusing solely on feature implementation.

Key improvements include:

### 🔄 Transaction-Safe Business Operations

Critical database workflows use transactions to roll back partial database
updates on failure. Gemini and Cloudinary calls are external side effects, not
participants in the database transaction.

---

### ☁️ Cloudinary Resource Cleanup

Images are automatically removed from Cloudinary whenever associated reports are permanently deleted, preventing unnecessary cloud storage usage.

---

### 🤖 AI Reliability

The AI integration includes:

- Retry Mechanism
- Timeout Handling
- Confidence Threshold Validation
- Structured JSON Parsing
- Graceful Error Handling

These safeguards improve reliability when communicating with external AI services.

---

### 📦 OpenFeign Integration

Google Gemini Vision is accessed through **OpenFeign**, providing a clean abstraction layer for external API communication.

This architecture makes it easier to replace or extend AI providers in the future.

---

### 🧩 Reusable Business Services

Complex operations such as report deletion and user deletion are encapsulated into dedicated services following the **Single Responsibility Principle (SRP)**.

Examples include:

- ReportDeletionService
- UserDeletionService
- AssignmentDeletionService

This keeps controllers lightweight while improving maintainability.

---

### 🚀 Performance Optimizations

Several optimizations were implemented across the backend, including:

- Optimized leaderboard queries
- Bounding Box filtering before Haversine calculations
- Eliminated N+1 query issues
- Reduced unnecessary database access
- Efficient aggregate repository queries
- Centralized business logic reuse
- **Redis caching for read-heavy aggregates** — homepage impact stats, leaderboards and both dashboards avoid repeated PostgreSQL aggregates on cache hits. No repository benchmark establishes latency. See **⚡ Redis Caching Architecture** above.
- **Redis-backed rate limiting stops downstream expensive work** — over-limit requests do not reach controllers, Gemini, Cloudinary uploads or business-service database operations. JWT identity resolution runs first and may already query account repositories. See **🚦 API Rate Limiting** above.

---

### 🛡 Robust Exception Handling

A centralized exception handling mechanism provides consistent API responses throughout the application.

Custom exceptions are used for business-specific scenarios, improving API clarity and simplifying frontend integration.

---

# 📈 Backend Evolution

The backend was developed incrementally, with successive changes extending the architecture while preserving modularity and maintainability. The repository does not define a verifiable count of major development phases.

Beginning with secure authentication and role-based access control (later extended with email-OTP verification and Google sign-in), the project gradually evolved into a comprehensive smart waste management platform featuring AI-powered report validation, intelligent duplicate detection, structured cleanup workflows, community engagement, analytics, public transparency, live leaderboards, and a production-ready administration system.

This phased, modular approach ensured that every feature was built upon a solid architectural foundation, resulting in a backend that is scalable, maintainable, and ready for frontend integration.

---

# 📁 Project Structure

The backend follows a clean, modular, and layered architecture to ensure scalability, maintainability, and separation of concerns.

```text
project root
├── pom.xml                       # Java 21 / Spring Boot 3.5.15
├── mvnw / mvnw.cmd / .mvn         # Maven wrapper
├── Dockerfile                    # Temurin 21; packages with tests skipped
├── .github/workflows/keep-backend-awake.yml
├── scripts
│   └── build_location_directory.py # Deterministic directory generator
└── src
    ├── main
    │   ├── java/com/cleanbharat/wastemanagement
    │   │   ├── client             # Gemini Feign client
    │   │   ├── config             # Security, cache, mail, AI and startup runners
    │   │   ├── controller         # 19 REST controllers
    │   │   ├── dto                # Requests/responses, including account recovery
    │   │   │   ├── admin
    │   │   │   ├── ai
    │   │   │   ├── common         # PageResponse
    │   │   │   └── gemini
    │   │   ├── entity             # 11 JPA entity files
    │   │   ├── enums              # 11 enum files
    │   │   ├── exception
    │   │   ├── mapper
    │   │   ├── repository         # 11 Spring Data repositories
    │   │   ├── security           # JWT and rate-limit filter
    │   │   ├── service
    │   │   │   ├── ai             # Gemini gateway/retry/parsing pipeline
    │   │   │   ├── deletion       # Assignment/report/user resource cleanup
    │   │   │   └── location       # Directory and duplicate-location validation
    │   │   ├── util
    │   │   └── WastemanagementApplication.java
    │   └── resources
    │       ├── application.properties # Protected environment-specific configuration
    │       ├── templates          # Currently empty
    │       ├── db                 # 7 operator/maintenance SQL scripts
    │       └── locations
    │           ├── india-ulb-directory.json
    │           ├── README.md
    │           └── source/Ulb_By_State_2026-10-18_16-34-45.xlsx
    └── test/java/com/cleanbharat/wastemanagement
        ├── config / controller / exception / repository / security
        ├── service/location / util
        └── WastemanagementApplicationTests.java
```

Each package has a single responsibility, making the codebase easier to understand, extend, and maintain. The current main-source inventory contains **217 Java files**; test inventory is described in **Run Automated Tests**.

---

# ⚙️ Getting Started

Follow these steps to set up the project locally.

---

## Prerequisites

Ensure the following software is installed on your system.

- Java 21
- Maven 3.9+
- PostgreSQL
- Redis (local Docker or managed cloud — required for dashboard / leaderboard caches, and for sign-up verification codes)
- Git
- IntelliJ IDEA (Recommended)
- Postman (For API Testing)
- Gmail App Password (optional — only for email-OTP sign-up; Google sign-in works without it)
- Google Cloud OAuth 2.0 Web client id (optional — only for Google sign-in)

---

## Clone the Repository

```bash
git clone https://github.com/RathoreSuhan/waste-management-backend.git

cd waste-management-backend
```

---

## Configure PostgreSQL

Create a PostgreSQL database.

Example

```sql
CREATE DATABASE wastemanagement;
```

---

## Configure Environment Variables

Use environment variables or a private external Spring configuration file. The
names below are a setup reference derived from safe source declarations and
Spring conventions, **not an inventory of the protected `application.properties`**.
No example configuration template is shipped. Blank entries must be supplied
locally; report-confidence/duplicate settings have no in-code defaults, so no
unverified numeric values are suggested here.

```properties
# Database

spring.datasource.url=

spring.datasource.username=

spring.datasource.password=

# JPA

spring.jpa.hibernate.ddl-auto=update

spring.jpa.show-sql=true

# JWT

jwt.secret=

jwt.expiration=

# Cloudinary

cloudinary.cloud-name=

cloudinary.api-key=

cloudinary.api-secret=

# Google Gemini

gemini.api.key=
gemini.model=
# Optional comma-separated fallback models
gemini.fallback-models=

# Google Sign-In
# Only the public OAuth client id. There is no client secret in this flow:
# the browser hands the backend a signed ID token, not an authorization code.
# Usually supplied as the GOOGLE_CLIENT_ID environment variable instead.

google.client-id=

# Sign-up email verification
# SMTP account the six-digit codes are sent from. Usually supplied as the
# MAIL_* environment variables instead; mail.password is a secret and must
# never be committed. host and port default to smtp.gmail.com:587.
# Gmail shows app passwords grouped with spaces - MailConfig strips them.

mail.username=

mail.password=

mail.host=

mail.port=

mail.from=

# Brevo HTTPS fallback (Render free tier blocks outbound SMTP ports 25/465/587,
# so codes go over port 443 when this key is set; SMTP is used otherwise).
# Sender address must be verified in the Brevo dashboard. Secret - never commit.

brevo.api-key=
# Optional overrides (defaults are documented in Authentication Configuration)
brevo.api-url=
brevo.sender-name=

# AI Configuration

report.ai.minimum-confidence=

# Duplicate Detection

report.duplicate.radius-meters=

report.duplicate.days=

# Redis Caching (connection URL is provided via environment, never committed)

# Example: SPRING_DATA_REDIS_URL=redis://default:<password>@<host>:<port>
# (or rediss://... for TLS). Spring Boot autoconfigures Lettuce from this URL.

# API Rate Limiting (all optional — these are the in-code defaults, shown for tuning)

ratelimit.enabled=true
ratelimit.window-seconds=60
ratelimit.public-per-minute=100
ratelimit.auth-per-minute=10
ratelimit.expensive-per-minute=5
ratelimit.community-per-minute=30
ratelimit.staff-per-minute=60
ratelimit.trusted-proxy-hops=1
```

> **Note:** Never commit API keys, secrets, or credentials to a public repository. Use environment variables or external configuration in production deployments.

---

## Install Dependencies

Download all required Maven dependencies and build the project.

```bash
mvn clean install
```

This command will:

- Download all project dependencies
- Compile the source code
- Execute unit and integration tests
- Generate the project artifact (`.jar`)

---

## Run the Application

Start the Spring Boot application using Maven.

```bash
mvn spring-boot:run
```

Alternatively, open the project in **IntelliJ IDEA** and run the `WastemanagementApplication` class directly.

---

## Run Automated Tests

Execute all **Unit Tests** and **Integration Tests**.

```bash
mvn test
```

The current static inventory has **37 test classes**, **411 `@Test` methods**
and **one parameterized method with seven cases**: **418 source-declared
invocations**, not a fresh passing result. The earlier **316 passing** result in
the Location rollout record is preserved as historical local validation.

**Full-suite safety check:** `WastemanagementApplicationTests` uses a bare
`@SpringBootTest` without datasource/service/migration overrides, and there are
no test resources or active test profiles. H2 availability and mocks in isolated
tests do not prove that the full context cannot reach PostgreSQL, Redis or
Cloudinary, or enable the location runner. **`mvn test` was skipped in this audit**;
confirm safe isolation for all three before running it. `mvn clean install` and
`mvn clean verify` also execute tests and require the same precaution.

The automated test suite includes:

- Unit Testing with JUnit 5
- Mocking with Mockito
- Integration Testing with Spring Boot Test
- H2 In-Memory Database

---

## Generate Code Coverage Report

Generate a **JaCoCo Code Coverage Report**.

```bash
mvn clean verify
```

After successful execution, the coverage report can be found at:

```text
target/site/jacoco/index.html
```

Open `index.html` in your browser to view detailed code coverage statistics.

---

## Verify the Application

Once the application starts successfully, it will be available at:

```text
http://localhost:8080
```

You can now:

- Test REST APIs using **Postman**
- Execute Unit & Integration Tests using **Maven**
- Review Code Coverage using **JaCoCo**

---

# 📡 API Overview

The backend exposes RESTful APIs organized by functional modules.

| Module | Purpose |
|---------|---------|
| Authentication | Registration, Login & JWT Authentication |
| Garbage Reports | Create, View & Manage Garbage Reports |
| Image Upload | Cloudinary Image Upload |
| Municipal Corporation | Admin-managed contact records and official ULB mappings; authenticated exact-ULB and legacy-city contact lookups |
| Municipal Console | Assignment-scoped proposal decisions, active cleanups & completion sign-off |
| Cleanup Proposals | Cleaner proposals and municipal decisions |
| Cleanup Activity Log | Optional progress updates during an active cleanup |
| Account | Own profile, picture and activity record; authenticated password change/recovery for users and corporations |
| Voting | Community Urgency Voting |
| Comments | Threaded Community Discussions |
| Analytics | Engagement Analytics & Dashboard |
| Cleanup Assignment | Cleaner Workflow Management |
| Rewards | Reward Summary & History |
| Public Feed | Officially completed, AI-verified cleanup showcase |
| Leaderboard | National, State, District and City rankings; signed-in cleaner personal ranking |
| Admin | Dashboard, User & Report Management |

The APIs generally return JSON DTOs, including paginated `PageResponse` results,
but file upload and some delete handlers return plain strings. Error responses
also have endpoint-specific AI/duplicate-report shapes; not every failure is
identical.

### Complete controller endpoint inventory

The **19 controllers declare 88 handler mappings** below. Public means
`permitAll`; authenticated/role labels describe `SecurityConfig`, with service
checks stated separately. `Cleaner`, `Citizen`, `Municipal` and `Admin` denote
`ROLE_CLEANER`, `ROLE_CITIZEN`, `ROLE_MUNICIPAL_OFFICER` and `ROLE_ADMIN`.
A public matcher is not a promise that an invalid attached JWT is ignored on
all public routes. Framework Actuator endpoints and the outbound Feign mapping
are not included in this controller count.

| Method | Path | Security / additional contract |
|---|---|---|
| `POST` | `/api/auth/register` | Public; OTP request/verified registration; Citizen/Cleaner only |
| `POST` | `/api/auth/google` | Public; verified Google identity, sign-in/link/registration-needed |
| `POST` | `/api/auth/google/register` | Public; Citizen/Cleaner registration with directory triple |
| `POST` | `/api/auth/login` | Public; User or corporation password login |
| `PATCH` | `/api/account/password` | Authenticated; own User/corporation password |
| `POST` | `/api/account/password/recovery/otp` | Authenticated; code sent to session identity |
| `POST` | `/api/account/password/recovery/verify` | Authenticated; own code → reset token |
| `POST` | `/api/account/password/recovery/confirm` | Authenticated; session-bound token consumption |
| `POST` | `/api/account/profile-picture` | Authenticated; generic upload (no User-row lookup) |
| `GET` | `/api/account/profile` | Authenticated; own User row |
| `GET` | `/api/account/record` | Authenticated; own User activity record |
| `DELETE` | `/api/account/profile-picture` | Authenticated; own User row |
| `PUT` | `/api/account/profile` | Authenticated; own User row |
| `POST` | `/api/reports` | Authenticated; service requires Citizen; multipart AI/location/duplicate validation |
| `GET` | `/api/reports` | Public; paginated keyword/status list |
| `GET` | `/api/reports/{id}` | Public; report detail |
| `GET` | `/api/reports/my` | Authenticated explicit exception; own User reports |
| `GET` | `/api/reports/my/summary` | Authenticated fallback; own User status totals |
| `POST` | `/api/files/upload` | Public; generic Cloudinary upload |
| `POST` | `/api/votes` | Citizen; create/update 1–5 urgency vote |
| `POST` | `/api/comments/report/{reportId}` | Authenticated; User row required |
| `POST` | `/api/comments/{commentId}/reply` | Authenticated; User row required |
| `GET` | `/api/comments/report/{reportId}` | Public; recursive discussion tree |
| `DELETE` | `/api/comments/{commentId}` | Authenticated; owner or Admin; cascades replies |
| `GET` | `/api/analytics/report/{reportId}` | Public |
| `GET` | `/api/analytics/trending` | Public |
| `GET` | `/api/analytics/dashboard` | Authenticated |
| `GET` | `/api/analytics/platform-impact` | Public; cached impact totals |
| `POST` | `/api/cleanup-assignments/{assignmentId}/start` | Authenticated; service Cleaner/owner/district/GPS guards; approved ASSIGNED or legacy CLAIMED |
| `POST` | `/api/cleanup-assignments/{assignmentId}/upload-image` | Authenticated; owner/district/GPS and working-state checks; no explicit Cleaner-role comparison in this method |
| `GET` | `/api/cleanup-assignments/my-tasks` | Authenticated; service Cleaner guard; own tasks |
| `GET` | `/api/cleanup-assignments/pending` | Authenticated; service Cleaner guard; district-eligible open work |
| `GET` | `/api/cleanup-assignments/claimed` | Authenticated; service Cleaner guard; own legacy CLAIMED work |
| `GET` | `/api/cleanup-assignments/in-progress` | Authenticated; service Cleaner guard; own IN_PROGRESS/REWORK_REQUIRED work |
| `GET` | `/api/cleanup-assignments/completed` | Authenticated; service Cleaner guard; own completed work |
| `GET` | `/api/cleanup-assignments/nearby` | Authenticated; service Cleaner guard; district-filtered open work, not a GPS-radius query |
| `POST` | `/api/cleanup-proposals/assignment/{assignmentId}` | Cleaner; open-site/district/inspection-GPS checks; optional inspection photo |
| `PUT` | `/api/cleanup-proposals/{proposalId}` | Cleaner; owner/district/editable-state checks |
| `DELETE` | `/api/cleanup-proposals/{proposalId}` | Cleaner; owner/editable-state checks; soft withdrawal |
| `GET` | `/api/cleanup-proposals/my` | Cleaner; own proposals |
| `GET` | `/api/cleanup-proposals/{proposalId}` | Cleaner; proposal owner |
| `POST` | `/api/cleanup-activity-logs/assignment/{assignmentId}` | Cleaner; owner/district; IN_PROGRESS/REWORK_REQUIRED; optional photo/GPS |
| `GET` | `/api/cleanup-activity-logs/assignment/{assignmentId}` | Cleaner; assignment owner |
| `DELETE` | `/api/cleanup-activity-logs/{activityLogId}` | Cleaner; log owner and working state |
| `GET` | `/api/cleanup-approvals/stats` | Municipal; own corporation |
| `GET` | `/api/cleanup-approvals/proposal-queue` | Municipal; own corporation |
| `GET` | `/api/cleanup-approvals/assignment/{assignmentId}/proposals` | Municipal; assigned-corporation guard |
| `POST` | `/api/cleanup-approvals/proposal/{proposalId}` | Municipal; assigned-corporation and proposal-state guards |
| `GET` | `/api/cleanup-approvals/completion-queue` | Municipal; own corporation |
| `POST` | `/api/cleanup-approvals/completion/{assignmentId}` | Municipal; assigned-corporation guard; AWAITING_APPROVAL |
| `GET` | `/api/cleanup-approvals/assignment/{assignmentId}/history` | Municipal; assigned-corporation guard |
| `GET` | `/api/cleanup-approvals/active-cleanups` | Municipal; own corporation |
| `GET` | `/api/cleanup-approvals/completed-cleanups` | Municipal; own corporation |
| `GET` | `/api/cleanup-approvals/assignment/{assignmentId}` | Municipal; assigned-corporation guard |
| `GET` | `/api/cleanup-approvals/assignment/{assignmentId}/activity-logs` | Municipal; assigned-corporation guard |
| `GET` | `/api/rewards/me` | Authenticated; service Cleaner guard |
| `GET` | `/api/rewards/history` | Authenticated; service Cleaner guard |
| `GET` | `/api/public-feed` | Public; paginated COMPLETED + AI-verified assignments |
| `GET` | `/api/public-feed/{reportId}` | Public; eligible completed cleanup |
| `POST` | `/api/public-feed/{reportId}/view` | Public; eligible completed cleanup |
| `POST` | `/api/public-feed/{reportId}/like` | Authenticated explicit exception; User account, one toggled like per account/report |
| `POST` | `/api/public-feed/{reportId}/share` | Public; eligible completed cleanup |
| `GET` | `/api/leaderboard` | Public; National Top 10 |
| `GET` | `/api/leaderboard/state/{state}` | Public; State Top 10 |
| `GET` | `/api/leaderboard/district/{state}/{district}` | Public; exact State/District pair |
| `GET` | `/api/leaderboard/city/{city}` | Public; unique directory ULB resolution or legacy city matching |
| `GET` | `/api/leaderboard/me` | Authenticated; service Cleaner guard; national standing |
| `GET` | `/api/locations` | Public; one-day HTTP cache; directory availability guard |
| `POST` | `/api/municipal-corporations` | Admin |
| `GET` | `/api/municipal-corporations` | Admin; paginated/searchable register |
| `GET` | `/api/municipal-corporations/me` | Municipal; session corporation |
| `GET` | `/api/municipal-corporations/{id}` | Admin |
| `GET` | `/api/municipal-corporations/city/{city}` | Authenticated explicit exception; legacy contact |
| `GET` | `/api/municipal-corporations/ulb/{ulbCode}` | Authenticated explicit exception; exact ULB contact |
| `PUT` | `/api/municipal-corporations/{id}` | Admin; mapping preserves existing city |
| `PUT` | `/api/municipal-corporations/me` | Municipal; own contacts/organization, not location mapping |
| `DELETE` | `/api/municipal-corporations/{id}` | Admin; subject to database relationships |
| `GET` | `/api/admin/reports/search` | Admin; paginated keyword search |
| `GET` | `/api/admin/reports/filter` | Admin; status/state/district filters |
| `DELETE` | `/api/admin/reports/{reportId}` | Admin; dependent-resource deletion |
| `GET` | `/api/admin/dashboard` | Admin; cached overview |
| `GET` | `/api/admin/users` | Admin; paginated optional role filter |
| `GET` | `/api/admin/users/search` | Admin; keyword/role search |
| `GET` | `/api/admin/users/{userId}` | Admin; user activity/detail |
| `DELETE` | `/api/admin/users/{userId}` | Admin; Admin deletion forbidden; Cleaner with assignments forbidden |
| `PUT` | `/api/admin/users/{userId}/promote` | Admin; Citizen-only promotion |
| `GET` | `/api/health` | Public; no-store health response; limiter-exempt |
| `GET` | `/test` | Authenticated; Gemini probe; expensive rate tier |

### Location-related API contract

| Access | Method & path | Contract |
|---|---|---|
| Public | `GET /api/locations` | Nested State → District → ULB directory; one-day public HTTP cache; `503` when the bundled directory is unavailable |
| Public | `POST /api/auth/register` | Password/OTP registration; `RegisterRequest` requires `stateCode`, `districtCode`, `ulbCode` on both calls |
| Public | `POST /api/auth/google/register` | Google registration with the same three-code location contract |
| Citizen | `POST /api/reports` (multipart) | V2 shape sends `stateCode`, `districtCode`, `ulbCode`; legacy shape sends `city`, `state`. Common fields include GPS/address/pincode and `image` |
| Public | `GET /api/leaderboard` | National Top 10 |
| Public | `GET /api/leaderboard/state/{state}` | State Top 10 |
| Public | `GET /api/leaderboard/district/{state}/{district}` | District Top 10 across all ULBs in the exact State/District pair |
| Public | `GET /api/leaderboard/city/{city}` | Existing City Top 10; retained for compatibility |
| Authenticated | `GET /api/municipal-corporations/ulb/{ulbCode}` | Exact location-V2 contact lookup; `404` on no configured corporation, with no city fallback |
| Authenticated | `GET /api/municipal-corporations/city/{city}` | Legacy contact lookup for reports whose `ulbCode` is absent |

All four geographic leaderboard GETs are public in `SecurityConfig` and bypass
JWT processing in `JwtAuthenticationFilter`. `GET /api/leaderboard/me` remains
authenticated and the service requires a cleaner account.

Non-exempt requests are additionally subject to **Redis-backed rate limiting** in the security filter chain. Exceeding a tier's allowance returns `429 Too Many Requests` with `Retry-After` and the filter's `ErrorResponse` JSON. Health, actuator-prefix and `OPTIONS` exemptions apply; other failures need not share this shape. See **🚦 API Rate Limiting**.

---

# 🧪 Testing

The existing documentation records developer-reported manual API checks and
historical local automated runs. This README audit checked source and test
inventory; it did not rerun Postman, live services or the Maven suite.

## Manual API Testing

The following Postman checks are **historical developer-reported coverage**, not
a fresh execution result from this documentation audit:

- User Registration & Login
- JWT Authentication
- Role-Based Authorization
- Garbage Reporting
- Image Upload
- AI Validation
- Duplicate Report Detection
- Community Voting
- Discussion System
- Analytics
- Cleaner Assignment Workflow
- Reward Generation
- Leaderboards
- Public Feed
- Admin Portal
- Exception Handling
- Business Validations

---

## Automated Testing

The project also includes automated testing using modern Java testing frameworks.

### Unit Testing

Implemented using:

- JUnit 5
- Mockito

Unit tests validate:

- Service Layer Business Logic
- Utility Methods
- Exception Scenarios
- AI Validation Logic
- Reward Calculation
- Duplicate Detection Logic

The municipal cleanup workflow has service-level and mocked-controller coverage, including:

- A cleaner cannot start work before the municipality approves the proposal
- A municipal corporation can only manage cleanups assigned to its authenticated corporation
- Approving a proposal assigns the proposing cleaner
- Rejected and revision-required proposals cannot be started
- Activity logs are optional and accepted in `IN_PROGRESS` and `REWORK_REQUIRED`
- Completion requires evidence, and GPS proximity is enforced on start and proof upload
- AI verification runs before the municipal completion decision
- No reward is generated before municipal approval; sequential repeat rewards are suppressed
- A cleanup reaches the public feed only after official completion
- Existing Citizen, Cleaner, and Admin permissions continue to work

Rate limiting is covered by **37 dedicated tests**, which assert that:

- Anonymous callers are keyed by IP and authenticated callers by their principal
- Client-supplied identifiers are ignored, and `X-Forwarded-For` is read from the trusted end
- Two users, two IPs, or two tiers never share a bucket — and one account cannot earn a second bucket by changing the case of its email
- Requests under the limit pass through; the next one returns `429` with `Retry-After`
- Keys stay inside the dedicated `rl:` namespace and never resemble a cache key
- The bucket expires when its window ends, not a full window later
- A percent-encoded path cannot slip into a more generous tier
- The filter runs exactly once per request, and a Redis outage fails **open**

---

### Integration Testing

Implemented using:

- Spring Boot Test
- H2 In-Memory Database

Integration tests verify:

- Repository Layer
- JPA Entity Relationships
- Database Operations
- REST API Endpoints
- Business workflows exercised with mocked collaborators rather than live end-to-end services

---

### Code Coverage

Code quality and test coverage are measured using **JaCoCo**, ensuring that critical business logic is thoroughly tested and helping identify untested code paths.

---

# 📈 Quality Assurance

To improve software reliability and maintainability, the backend follows modern testing and quality assurance practices.

- ✅ Unit Testing with JUnit 5
- ✅ Integration Testing with Spring Boot Test & H2 Database
- ✅ Mocking with Mockito
- ✅ Code Coverage Analysis using JaCoCo
- ✅ Manual API Testing using Postman
- ✅ Centralized Exception Handling
- ✅ Business Rule Validation
- ✅ Layered Architecture for Better Testability

---

# 🔒 Security Features

Security has been a core focus throughout development.

Implemented features include:

- JWT Authentication
- Spring Security
- BCrypt Password Encryption
- Email-Code Verification (ownership proof before sign-up)
- Google ID-Token Verification (OAuth 2.0, no client secret)
- Stateless Authentication
- Role-Based Access Control
- Protected Endpoints
- Distributed API Rate Limiting (Redis-backed, per-tier `429` + `Retry-After`)
- Global Exception Handling
- Secure Password Storage
- Business Rule Validation

---

# 🚀 Future Enhancements

Although the backend is feature-rich, several exciting enhancements are planned.

## Frontend

- React and Tailwind frontend already implemented; extend existing responsive/accessibility coverage
- Progressive Web App (PWA)

---

## Maps & Location

- Google Maps Integration
- Live Cleaner Tracking
- Nearby Report Search
- Navigation Support

---

## Notifications

- Email Notifications
- Push Notifications
- SMS Alerts

---

## Artificial Intelligence

- Extend current garbage-category and severity AI outputs
- Illegal Dumping Detection
- AI Heat Maps
- Predictive Waste Analytics

---

## Performance

- Redis cache tuning (hit-rate metrics, stampede guards for hotter keys)
- Extend the existing Java 21 Dockerfile into a verified deployment pipeline
- Kubernetes
- CI/CD Pipeline (the existing GitHub workflow is a keep-awake ping, not a build/test pipeline)
- Monitoring & Logging

---

## Mobile Support

- Android Application
- iOS Application
- QR Code Reporting
- Offline Report Submission

---

# 📚 What I Learned

This project provided hands-on experience with building a production-oriented backend using modern Java technologies while applying software engineering best practices throughout the development lifecycle.

Key areas explored include:

- Spring Boot
- Spring Security
- JWT Authentication
- REST API Development
- PostgreSQL
- Hibernate & JPA
- Cloudinary Integration
- Google Gemini Vision API
- OpenFeign
- Role-Based Access Control (RBAC)
- AI Prompt Engineering
- Geospatial Algorithms
- Haversine Distance Calculation
- Layered Architecture
- Exception Handling
- Transaction Management
- Redis Caching with Spring Cache Abstraction (`@Cacheable` / `@CacheEvict`, TTL + eviction)
- Cache consistency for multi-tenant dashboards (per-city keys, shared admin key)
- Distributed Rate Limiting (fixed-window counters shared across instances)
- Atomic Redis operations with Lua scripting (read-modify-write without a race)
- Servlet Filter Chain Design (why a filter, not `@RestControllerAdvice`, owns the `429`)
- Clean Code Principles
- Scalable Backend Design
- Unit Testing with JUnit 5
- Integration Testing with H2 Database
- Mocking using Mockito
- Code Coverage Analysis with JaCoCo
- API Testing using Postman
- Production-Oriented Backend Development

---

# 🤝 Contributing

Contributions, suggestions, and constructive feedback are always welcome.

If you'd like to improve the project:

1. Fork the repository.
2. Create a new feature branch.
3. Commit your changes.
4. Push to your fork.
5. Open a Pull Request with a clear description of your changes.

Please ensure that new features follow the existing architecture and coding standards.

---

# 📜 License

This project is currently released for educational and portfolio purposes.

If you intend to reuse or extend this project, please provide appropriate attribution.

You may choose an open-source license such as **MIT License** in the future if the project becomes publicly maintained.

---

# 🙏 Acknowledgements

Special thanks to the open-source community and the technologies that made this project possible.

- Spring Boot
- Spring Security
- Spring Data Redis
- Redis
- Hibernate
- PostgreSQL
- Cloudinary
- Google Gemini Vision API
- OpenFeign
- JUnit 5
- Mockito
- H2 Database
- JaCoCo
- Maven
- IntelliJ IDEA
- Postman
- Git & GitHub

These technologies played a significant role in building a scalable, AI-powered waste management platform.

---

# 👨‍💻 Creator

## Suhan Kumar Singh

**Backend Developer | Java & Spring Boot Enthusiast | AI-Driven Application Developer**

Clean Bharat was designed and developed as a comprehensive full-stack software engineering project with a strong emphasis on backend architecture, secure system design, artificial intelligence integration, and scalable application development.

Throughout this project, the primary focus was not only to build functional features but also to apply industry-standard software engineering principles such as layered architecture, clean code practices, modular design, role-based security, AI integration, production-oriented workflows, and performance optimization.

This project reflects a passion for solving real-world problems through technology and demonstrates practical experience in designing intelligent, scalable, and maintainable backend systems.

---

> **"Technology becomes meaningful when it solves real-world problems. Clean Bharat is a step toward leveraging Artificial Intelligence to build cleaner, smarter, and more connected communities."**

---

⭐ **If you found this project interesting, consider giving it a star on GitHub. Your support is greatly appreciated!**

---

**Thank you for visiting the repository!**