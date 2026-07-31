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

Only AI-verified cleanups are marked as completed.

---

### 🏆 Reward & Leaderboard System

Cleaners earn reward points only after successful AI verification.

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

Every successfully completed and AI-verified cleanup is automatically published to a public feed where anyone can view:

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
Automatic Municipal Assignment
    │
    ▼
Cleaner Claims Assignment
    │
    ▼
Cleaner Starts Cleanup
    │
    ▼
Cleaner Uploads Cleanup Image
    │
    ▼
Google Gemini Cleanup Verification
    │
    ▼
Reward Generated
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
| 🧹 Cleaner | Claims assignments and uploads cleanup proof |
| 👨‍💼 Admin | Complete platform management |

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

The backend uses **PostgreSQL** as the primary relational database.

Major Entities

- User
- GarbageReport
- MunicipalCorporation
- Vote
- Comment
- CleanupAssignment
- RewardHistory
- PublicFeedAnalytics

Entity Relationship Overview

```text
                     User
                      │
          ┌───────────┼────────────┐
          │           │            │
          ▼           ▼            ▼
 GarbageReport      Vote       Comment
          │           │            │
          │           └────────────┘
          │
          ▼
 CleanupAssignment
          │
     ┌────┴─────────┐
     ▼              ▼
RewardHistory   PublicFeedAnalytics
```

Relationship Summary

| Relationship | Type |
|-------------|------|
| User → Reports | One-to-Many |
| User → Votes | One-to-Many |
| User → Comments | One-to-Many |
| Report → Votes | One-to-Many |
| Report → Comments | One-to-Many |
| Report → Cleanup Assignment | One-to-One |
| Cleanup Assignment → Reward History | One-to-One |
| Cleanup Assignment → Public Feed Analytics | One-to-One |

The schema is fully normalized and designed to minimize redundancy while supporting efficient querying.

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

Once a garbage report successfully passes AI validation and duplicate detection, the platform automatically initiates the cleanup process.

Unlike traditional complaint systems where reports remain static until manually assigned, Clean Bharat creates a structured workflow that tracks the complete lifecycle of every cleanup operation.

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
Cleanup Assignment Generated
          │
          ▼
Cleaner Claims Assignment
          │
          ▼
Cleaner Starts Cleanup
          │
          ▼
Uploads Cleanup Image
          │
          ▼
Google Gemini AI Verification
          │
          ▼
Reward Generated
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
| **PENDING** | Assignment created and waiting for a cleaner |
| **CLAIMED** | Accepted by a cleaner |
| **IN_PROGRESS** | Cleanup work has started |
| **COMPLETED** | AI verified and successfully completed |

Maintaining these states enables accurate progress tracking while preventing conflicting operations.

---

## Cleaner Dashboard

Dedicated APIs provide cleaners with a personalized workspace.

Cleaners can:

- View Pending Assignments
- View Claimed Assignments
- View Assignments In Progress
- View Completed Assignments
- View Their Personal Tasks
- View Nearby Assignments *(prepared for future Google Maps integration)*

Role-based authorization ensures only authenticated cleaners can access these resources.

---

# 🏆 Reward System

The reward system motivates cleaner participation by recognizing verified cleanup efforts instead of merely tracking completed assignments.

Rewards are generated **only after successful AI verification**.

---

## Reward Generation

```text
Cleanup Completed
        │
        ▼
Gemini AI Verification
        │
        ▼
Verification Passed
        │
        ▼
Reward Created
        │
        ▼
Reward Points Added
        │
        ▼
Leaderboard Updated
```

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

Instead of hiding successful cleanup operations inside dashboards, the platform automatically publishes AI-verified cleanups to a dedicated public feed.

The feed remains accessible **without authentication**, allowing anyone to explore successful sanitation efforts.

---

## Community Success Flow

```text
Cleanup Completed
        │
        ▼
Gemini Verification
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
| Municipal Corporation | Municipal Contact Management |
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