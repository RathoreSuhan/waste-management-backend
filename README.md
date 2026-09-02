# 🇮🇳 Clean Bharat
### AI-Powered Smart Waste Management Platform

> **Clean Bharat** is a modern AI-powered waste management platform that leverages **Artificial Intelligence, Computer Vision, Cloud Computing, and Community Participation** to create a transparent, efficient, and intelligent garbage reporting and cleanup ecosystem.
>
> The platform connects **Citizens**, **Cleaners**, **Municipal Corporations**, and **Administrators** through a unified digital system where garbage reports are **AI-validated**, duplicate reports are automatically prevented, cleanup work is verified using **Google Gemini Vision AI**, and successful cleanups are transparently showcased to the public.

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

Cleaners earn reward points only after the city Municipal Corporation approves the completed cleanup. GPS proof and AI verification are prerequisites, and every cleanup is rewarded **exactly once**.

The platform automatically generates:

- Reward History
- National Leaderboard
- State Leaderboard
- City Leaderboard
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
Reward Generated (Once)
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
Cloudinary Upload
        │
        ▼
Download Image
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
Cleanup Approved
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
| 🔐 Authentication | Secure JWT-based authentication with role-based authorization |
| 📝 Garbage Reporting | Citizens can report garbage with images and GPS location |
| ☁️ Cloudinary Integration | Cloud storage for garbage and cleanup images |
| 📍 Smart Location Module | GPS coordinates, structured address, city, state, and pincode |
| 🏛 Municipal Management | Admin-managed municipal corporation database |
| ⭐ Community Voting | Citizens prioritize reports through a 5-star urgency system |
| 💬 Community Discussion | Threaded comments with unlimited nested replies |
| 📊 Engagement Analytics | Trending reports and dashboard analytics |
| 🧹 Cleaner Workflow | Complete assignment lifecycle from claim to completion |
| 🤖 AI Cleanup Verification | Google Gemini Vision verifies completed cleanups |
| 🏆 Reward System | Reward history and cleaner recognition |
| 🌐 Public Feed | Public showcase of AI-verified cleanups |
| 🥇 Live Leaderboard | National, State, and City rankings |
| 👨‍💼 Admin Portal | User management, report moderation, analytics, and deletion workflows |

---

# 🛠 Technology Stack

## Backend

| Technology | Purpose |
|------------|----------|
| Java 21 | Primary Programming Language |
| Spring Boot 3.5.x | Backend Framework |
| Spring Security | Authentication & Authorization |
| JWT | Stateless Authentication |
| Spring Data JPA | ORM Layer |
| Hibernate | Database Persistence |
| PostgreSQL | Relational Database |
| Maven | Dependency Management |
| Lombok | Boilerplate Code Reduction |
| OpenFeign | External AI API Communication |
| JUnit 5 | Unit & Integration Testing Framework |
| Mockito | Mocking Framework for Unit Testing |
| H2 Database | In-Memory Database for Integration Testing |
| JaCoCo | Code Coverage Analysis |

---

## Artificial Intelligence

| Technology | Purpose |
|------------|----------|
| Google Gemini Vision AI | Garbage Detection & Cleanup Verification |

---

## Cloud Services

| Technology | Purpose |
|------------|----------|
| Cloudinary | Secure Cloud Image Storage |

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

The project follows a **Layered Architecture** with clear separation of responsibilities.

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

- **Controllers** handle HTTP requests and responses.
- **Services** contain business rules, AI integrations, and workflow logic.
- **Repositories** interact with the PostgreSQL database using Spring Data JPA.
- **Entities** model the domain and maintain relationships.
- **DTOs** provide secure data transfer between the backend and frontend.

This architecture promotes scalability, maintainability, testability, and clean separation of concerns.

The backend is fully functional and production-oriented, supporting secure authentication, AI-powered validation, intelligent report management, community engagement, cleaner workflows, administrative tools, and public transparency.

# 🏗 Backend Architecture & Implementation

The backend of **Clean Bharat** follows a **production-oriented layered architecture** built with **Spring Boot**. Every module is designed with scalability, maintainability, and separation of concerns in mind.

The application follows the standard flow:

```text
                    Client (React / Mobile)
                             │
                             ▼
                      REST Controllers
                             │
                             ▼
                     Service Layer
             (Business Logic & AI Workflow)
                             │
                             ▼
                    Repository Layer
                             │
                             ▼
                     PostgreSQL Database
```

Each layer has a dedicated responsibility.

| Layer | Responsibility |
|--------|----------------|
| Controller | Handles HTTP requests and responses |
| Service | Contains business rules and workflow logic |
| Repository | Performs database operations using Spring Data JPA |
| Entity | Represents database tables and relationships |
| DTO | Transfers data between backend and frontend |
| Security | Handles JWT authentication and authorization |
| Exception | Provides centralized error handling |

This architecture keeps controllers lightweight, encapsulates business logic inside services, and ensures that database access remains isolated from presentation concerns.

---

# 📂 Backend Modules

The backend is divided into multiple independent modules, each responsible for a specific domain of the application.

---

## 🔐 Authentication & Authorization

Authentication is implemented using **Spring Security** and **JWT (JSON Web Token)**.

### Features

- User Registration
- User Login
- BCrypt Password Encryption
- Stateless Authentication
- Role-Based Authorization (RBAC)
- JWT Token Generation & Validation
- Protected REST APIs

### Supported Roles

| Role | Description |
|------|-------------|
| 👤 Citizen | Reports garbage, votes, comments, tracks reports |
| 🧹 Cleaner | Proposes cleanups, executes approved work, uploads cleanup proof |
| 🏛 Municipal Corporation | Approves proposals and signs off completed cleanups **for its own city only** |
| 👨‍💼 Admin | Complete platform management |

A Municipal Corporation is **not** a self-registered account. It is a row in the `municipal_corporations` table created by an Admin, and only the email stored on that row can sign in to the Municipal Console.

> The `MUNICIPAL` cleaner type only describes the kind of cleanup crew. It never grants Municipal Console access.

Authentication Flow

```text
Client Login
      │
      ▼
Username + Password
      │
      ▼
Spring Security
      │
      ▼
JWT Generated
      │
      ▼
Client Stores JWT
      │
      ▼
Authorization Header

Bearer <JWT>

      │
      ▼
JWT Filter
      │
      ▼
Authenticated Request
```

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
```

The report becomes the central entity used by almost every module in the application.

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

Instead of storing location as a plain string, every report contains structured geographical information.

Supported fields

- Latitude
- Longitude
- Address
- Landmark
- City
- State
- Pincode

Benefits

- Accurate report mapping
- Future Google Maps integration
- Cleaner assignment
- Duplicate detection
- Nearby report search
- Municipal mapping

This design allows future GIS-based features without modifying the database structure.

---

# 🏛 Municipal Corporation Module

Municipal Corporations are managed dynamically instead of using hardcoded city mappings.

Administrators can manage:

- Organization Name
- City
- Phone Number
- Email

Each report automatically maps to the appropriate Municipal Corporation using its city information.

Benefits

- Easy maintenance
- Dynamic expansion
- No code changes for new cities

## Municipal Corporation Login

One city maps to exactly one Municipal Corporation, and that corporation is the only account able to operate the Municipal Console for that city.

| Rule | Behaviour |
|------|-----------|
| Account creation | Only an Admin can register a corporation (city, organization, phone, email) |
| Login identity | The corporation's registered email — nothing else resolves to municipal authority |
| Default password | Issued automatically when the corporation row is created (documented for the operator, never printed by the API) |
| Password change | `PUT /api/account/password` works for corporations exactly as it does for users |
| Self-registration | Blocked — `/api/auth/register` rejects `ROLE_MUNICIPAL_OFFICER` and rejects any email already used by a corporation |
| Data scope | Every municipal endpoint resolves the corporation from the JWT, so one city can never read or decide another city's cleanups |

```text
Admin adds Municipal Corporation (city + email)
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
Municipal Console scoped to that city only
```

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

The backend uses **PostgreSQL** in production and an in-memory **H2** database for automated tests.
Hibernate generates the schema from the JPA entities (`spring.jpa.hibernate.ddl-auto=update`), so every
table listed below maps one-to-one to a class in `com.cleanbharat.wastemanagement.entity`.

## Tables at a Glance

| # | Table | Entity | Purpose |
|---|-------|--------|---------|
| 1 | `users` | `User` | Every account — citizen, cleaner, municipal officer, admin |
| 2 | `garbage_reports` | `GarbageReport` | Citizen-submitted garbage complaints |
| 3 | `municipal_corporations` | `MunicipalCorporation` | City-wise civic body (also the officer login owner) |
| 4 | `cleanup_assignments` | `CleanupAssignment` | Work order raised for a report, owned by one city corporation |
| 5 | `cleanup_proposals` | `CleanupProposal` | Cleaner's site inspection + cleanup plan for an assignment |
| 6 | `cleanup_approvals` | `CleanupApproval` | Municipal decision trail — proposal stage **and** completion stage |
| 7 | `cleanup_activity_logs` | `CleanupActivityLog` | Optional progress notes posted while cleaning is in progress |
| 8 | `votes` | `Vote` | One urgency up-vote per user per report |
| 9 | `comments` | `Comment` | Threaded discussion on a report |
| 10 | `reward_history` | `RewardHistory` | Points ledger for cleaners |
| 11 | `public_feed_analytics` | `PublicFeedAnalytics` | Likes / views of a completed cleanup story |

## ER Diagram — Accounts & Community

```text
                 ┌──────────────────────────────┐
                 │            users             │
                 │──────────────────────────────│
                 │ id (PK)                      │
                 │ name                         │
                 │ email (unique)               │
                 │ password (BCrypt)            │
                 │ role            <enum>       │
                 │ cleaner_type    <enum, null> │
                 │ points                       │
                 │ created_at                   │
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
                 │ address / city               │
                 │ status          <enum>       │
                 │ urgency_score                │
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
   │ (see diagram above)          │      │ city / organization_name     │
   │                              │      │ phone / email                │
   └──────────────┬───────────────┘      └──────────────┬───────────────┘
                  │ 1:1 active work order               │ 1:N jobs of that city
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
| `garbage_reports` | `user_id` | `users` | No | Reporting citizen |
| `cleanup_assignments` | `report_id` | `garbage_reports` | No | One active work order per report |
| `cleanup_assignments` | `municipal_corporation_id` | `municipal_corporations` | No | City body that owns the job |
| `cleanup_assignments` | `cleaner_id` | `users` | **Yes** | Empty until a proposal is approved |
| `cleanup_proposals` | `assignment_id` | `cleanup_assignments` | No | Cascade ALL + orphan removal from parent |
| `cleanup_proposals` | `cleaner_id` | `users` | No | Unique with `assignment_id` |
| `cleanup_approvals` | `assignment_id` | `cleanup_assignments` | No | Assignment being reviewed |
| `cleanup_approvals` | `proposal_id` | `cleanup_proposals` | **Yes** | Set for `PROPOSAL` stage, `NULL` for `COMPLETION` |
| `cleanup_approvals` | `municipal_corporation_id` | `municipal_corporations` | No | Reviewing city body |
| `cleanup_approvals` | `decided_by` | `users` | No | Officer accountability |
| `cleanup_activity_logs` | `assignment_id` | `cleanup_assignments` | No | Cascade ALL + orphan removal from parent |
| `cleanup_activity_logs` | `cleaner_id` | `users` | No | Author of the progress note |
| `votes` | `user_id`, `report_id` | `users`, `garbage_reports` | No | Unique pair — one vote per user per report |
| `comments` | `user_id`, `report_id` | `users`, `garbage_reports` | No | Discussion author + target report |
| `comments` | `parent_comment_id` | `comments` | **Yes** | Self reference → threaded replies |
| `reward_history` | `cleaner_id` | `users` | No | Cleaner who earned the points |
| `reward_history` | `assignment_id` | `cleanup_assignments` | No | Cleanup the points were credited for |
| `public_feed_analytics` | `cleanup_assignment_id` | `cleanup_assignments` | No | `@OneToOne` + unique — one row per completed cleanup |

## Table Notes

- **`users`** — one table for all four roles; `cleaner_type` is filled only for cleaners.
  `ROLE_MUNICIPAL_OFFICER` accounts are city-scoped, so an officer only ever sees the reports and
  assignments of their own corporation.
- **`municipal_corporations`** — admin-managed master data (city, organisation name, phone, email).
  It is both the public helpline directory and the owner of every assignment raised in that city.
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
  gate it belongs to, `decision` says the outcome, `decided_by` says which officer signed it, and
  `decided_at` is stamped once (decisions are never edited, only new rows are added).
- **`cleanup_activity_logs`** — optional notes (`description`, `activity_at`, optional `image_url` and
  GPS) that a cleaner may post only while the assignment is `IN_PROGRESS`.
- **`reward_history`** — append-only ledger; the cached `users.points` total is derived from it.
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
| `cleanup_approvals.decision` | `ApprovalDecision` | `APPROVED`, `REJECTED`, `REVISION_REQUIRED` |

`CLAIMED` is kept only so historical rows from the older direct-claim flow still load; new assignments
never enter that state.

Enums that are **never** stored in the database (runtime / API only):

| Enum | Where it lives |
|------|----------------|
| `BadgeType` (`BRONZE`, `SILVER`, `GOLD`) | Computed on the fly in `LeaderboardServiceImpl.calculateBadge()` from `users.points` |
| `ImageRejectionReason` | AI validation responses and `InvalidReportImageException` only |
| `LeaderboardType` | Request parameter for leaderboard scope |

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Enum as STRING** | Human-readable rows and no ordinal corruption when a new enum value is added |
| **Nullable `cleaner_id`** | An assignment exists before anyone is selected, so the winner is stamped only at approval time |
| **One approvals table, two stages** | Proposal review and completion review share the same audit shape, so `stage` avoids a duplicate table |
| **Unique `(assignment_id, cleaner_id)`** | Competitive bidding without duplicate proposals from the same cleaner |
| **Unique `(user_id, report_id)` on votes** | Prevents double voting on urgency |
| **Self-referencing `comments`** | Threaded replies without a separate replies table |
| **Append-only ledger + cached points** | `reward_history` keeps the audit trail while `users.points` keeps leaderboard queries fast |
| **City scoping** | `municipal_corporation_id` on assignments/approvals keeps every officer inside their own city's data |

## Schema Maintenance

`spring.jpa.hibernate.ddl-auto=update` only ever **adds** to a live schema. It creates tables, columns
and indexes, but it never widens a column, never drops a `NOT NULL`, never removes an old `UNIQUE` rule
and never rewrites a stale `CHECK` constraint. So a database created months ago can keep rejecting
writes that today's entities consider perfectly valid.

Four startup runners repair exactly those gaps. Each one is PostgreSQL-only, idempotent (a no-op on
every later boot), logs a warning instead of failing startup, and has a hand-runnable SQL twin in
`src/main/resources/db/` for repairing a database without deploying.

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

`db/backfill-report-status-in-progress.sql` is a one-time data fix, not a schema repair, and has no
runner — it is meant to be executed by hand.

---

# 🔒 Security Architecture

Security is implemented using **Spring Security** with JWT authentication.

Main Components

- SecurityConfig
- JwtAuthenticationFilter
- JwtService
- CustomUserDetailsService

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

Controller
```

Security Features

- Stateless Sessions
- BCrypt Password Hashing
- JWT Expiration
- Role-Based Access
- Endpoint Protection
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

Only then is the cleanup approved.

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
Reward Generated (Exactly Once)
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

- A cleanup cannot be started unless a matching `PROPOSAL` + `APPROVED` approval record exists.
- Only the assigned cleaner can start work, log activity, or upload proof for that assignment.
- Start and proof uploads are rejected beyond **50 metres** from the reported site, and also when the device reports no location at all.
- Proof cannot be uploaded before the cleanup is started or after it is officially completed.
- Activity logs are **optional**, and are only accepted while the cleanup is actively in progress.

---

## Cleaner Dashboard

Dedicated APIs provide cleaners with a personalized workspace.

Cleaners can:

- Browse Open Sites in their own state/city and submit proposals
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

The Municipal Corporation of a city gets a dedicated workspace scoped strictly to its own municipality.

Corporations can:

- View city dashboard statistics
- Review the proposal queue and approve, reject, or request revision
- Monitor active cleanups and their activity logs
- Review AI-verified completion proof and sign off or send back for rework
- Read the full approval history of any assignment in their city

Every request resolves the corporation from the signed-in email, so a corporation can never read or decide another city's cleanups.

---

# 🏆 Reward System

The reward system motivates cleaner participation by recognizing officially verified cleanup efforts instead of merely tracking completed assignments.

Rewards are generated **only after the Municipal Corporation approves the completion**, and every assignment can be rewarded **only once**.

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

The reward ledger is keyed on the assignment, so repeated approval calls, retries, or duplicated requests can never inflate a cleaner's points.

---

## Reward History

Rather than storing only total points, the platform maintains a complete reward ledger.

Example

```text
Cleaner Rewards

+50   Report #21

+50   Report #25

+25   Bonus Campaign

+10   Community Drive
```

Benefits include:

- Complete reward history
- Cleaner transparency
- Future incentive campaigns
- Auditable reward calculations

---

# 🥇 Live Leaderboard System

To recognize outstanding contributions, Clean Bharat implements a live leaderboard system without introducing an additional leaderboard table.

Instead, rankings are calculated dynamically using existing project data.

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
| 🌆 City | Top cleaners within a city |
| 👤 Personal | Individual cleaner ranking |

---

## Badge System

Cleaners automatically receive achievement badges based on accumulated reward points.

| Points | Badge |
|---------|-------|
| 0 – 199 | 🥉 Bronze |
| 200 – 499 | 🥈 Silver |
| 500+ | 🥇 Gold |

---

## Competition Ranking

Instead of assigning sequential ranks, the backend implements **Competition Ranking**.

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
2
4
```

This provides fair rankings while handling ties correctly.

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

The feed remains accessible **without authentication**, allowing anyone to explore successful sanitation efforts.

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
Public Feed Record Created
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
- Cleaners with claimed assignments cannot be removed.
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

This guarantees database consistency without leaving orphaned records.

---

# ⚙️ Production-Level Optimizations

Throughout development, the backend was continuously refined to follow production-oriented engineering practices rather than focusing solely on feature implementation.

Key improvements include:

### 🔄 Transaction-Safe Business Operations

Critical workflows execute atomically to prevent partial database updates during failures.

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

---

### 🛡 Robust Exception Handling

A centralized exception handling mechanism provides consistent API responses throughout the application.

Custom exceptions are used for business-specific scenarios, improving API clarity and simplifying frontend integration.

---

# 📈 Backend Evolution

The backend was developed incrementally through **13 major phases**, with each phase extending the architecture while preserving modularity and maintainability.

Beginning with secure authentication and role-based access control, the project gradually evolved into a comprehensive smart waste management platform featuring AI-powered report validation, intelligent duplicate detection, structured cleanup workflows, community engagement, analytics, public transparency, live leaderboards, and a production-ready administration system.

This phased, modular approach ensured that every feature was built upon a solid architectural foundation, resulting in a backend that is scalable, maintainable, and ready for frontend integration.

---

# 📁 Project Structure

The backend follows a clean, modular, and layered architecture to ensure scalability, maintainability, and separation of concerns.

```text
src
└── main
    ├── java
    │   └── com.cleanbharat.wastemanagement
    │       ├── client                # External API clients (Gemini AI)
    │       ├── config                # Security, Cloudinary & AI configurations
    │       ├── controller            # REST Controllers
    │       ├── dto                   # Request & Response DTOs
    │       ├── entity                # JPA Entities
    │       ├── enums                 # Application Enums
    │       ├── exception             # Global & Custom Exceptions
    │       ├── repository            # Spring Data JPA Repositories
    │       ├── security              # JWT & Spring Security
    │       ├── service               # Business Logic
    │       ├── util                  # Helper Utilities
    │       └── WasteManagementApplication.java
    │
    └── resources
        ├── application.properties
        ├── db                        # One-time SQL maintenance scripts
        └── static
```

Each package has a single responsibility, making the codebase easier to understand, extend, and maintain.

---

# ⚙️ Getting Started

Follow these steps to set up the project locally.

---

## Prerequisites

Ensure the following software is installed on your system.

- Java 21
- Maven 3.9+
- PostgreSQL
- Git
- IntelliJ IDEA (Recommended)
- Postman (For API Testing)

---

## Clone the Repository

```bash
git clone https://github.com/<your-username>/clean-bharat.git

cd clean-bharat
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

Update your `application.properties` file.

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

# AI Configuration

app.ai.confidence-threshold=0.85

# Duplicate Detection

app.duplicate.radius-meters=100

app.duplicate.max-age-days=30
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

Alternatively, open the project in **IntelliJ IDEA** and run the `WasteManagementApplication` class directly.

---

## Run Automated Tests

Execute all **Unit Tests** and **Integration Tests**.

```bash
mvn test
```

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
| Municipal Corporation | Municipal Contact Management (Admin) |
| Municipal Console | City-scoped proposal decisions, active cleanups & completion sign-off |
| Cleanup Proposals | Cleaner proposals and municipal decisions |
| Cleanup Activity Log | Optional progress updates during an active cleanup |
| Account | Password change for users and municipal corporations |
| Voting | Community Urgency Voting |
| Comments | Threaded Community Discussions |
| Analytics | Engagement Analytics & Dashboard |
| Cleanup Assignment | Cleaner Workflow Management |
| Rewards | Reward Summary & History |
| Public Feed | AI Verified Cleanup Showcase |
| Leaderboard | National, State & City Rankings |
| Admin | Dashboard, User & Report Management |

The APIs follow REST principles and consistently return structured JSON responses with proper HTTP status codes.

---

# 🧪 Testing

The backend has been validated through a combination of **Manual API Testing**, **Unit Testing**, and **Integration Testing** to ensure correctness, reliability, and maintainability.

## Manual API Testing

The complete REST API workflow was tested using **Postman**, including:

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

The municipal cleanup workflow is covered end to end, including:

- A cleaner cannot start work before the municipality approves the proposal
- A municipal corporation can only manage cleanups in its own city
- Approving a proposal assigns the proposing cleaner
- Rejected and revision-required proposals cannot be started
- Activity logs are optional and only accepted while work is in progress
- Completion requires evidence, and GPS proximity is enforced on start and proof upload
- AI verification runs before the municipal completion decision
- No reward is generated before municipal approval, and never more than one per cleanup
- A cleanup reaches the public feed only after official completion
- Existing Citizen, Cleaner, and Admin permissions continue to work

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
- End-to-End Business Workflows

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
- Stateless Authentication
- Role-Based Access Control
- Protected Endpoints
- Global Exception Handling
- Secure Password Storage
- Business Rule Validation

---

# 🚀 Future Enhancements

Although the backend is feature-rich, several exciting enhancements are planned.

## Frontend

- React.js
- Tailwind CSS
- Responsive UI
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

- Automatic Garbage Classification
- Garbage Severity Prediction
- Illegal Dumping Detection
- AI Heat Maps
- Predictive Waste Analytics

---

## Performance

- Redis Caching
- Docker Deployment
- Kubernetes
- CI/CD Pipeline
- API Rate Limiting
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