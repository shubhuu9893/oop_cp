# CacheBrowse

**CacheBrowse is a React + Vite UI hosted by Electron's Chromium engine, with the Java/Maven backend providing application services.** Websites render in isolated Chromium `WebContentsView` tabs inside CacheBrowse; Vite-only mode is available for UI development but cannot render arbitrary websites.

## Run locally

Requirements: Node.js/npm, Java 21, Maven, and a graphical desktop session. From `frontend`, install packages once and launch the complete app:

```sh
cd frontend
npm install
npm run dev
```

Backend Run
mvn exec:java 

This starts Vite at `http://127.0.0.1:5173`, opens the CacheBrowse Electron window, and starts Java on `http://127.0.0.1:8080` if the API is not already available. Wait for the UI and backend readiness messages in the terminal. Chromium navigation and protection run in the Electron window; merely opening the Vite URL in Chrome is not running CacheBrowse's browser engine.

For individual services, run `mvn -q exec:java` from the repository root to start Java separately, then use `npm run dev:web` in `frontend` for UI-only development. Vite-only mode deliberately does not navigate external pages: a normal web page cannot embed arbitrary cross-origin websites reliably or control another browser's tabs. Use `npm run dev` for actual browsing. `npm run build` builds the React UI for the Electron host; `npm run smoke:desktop` runs the Chromium integration smoke test when the desktop app is already running with remote debugging enabled.

`SEARCH_ENGINE_URL` configures the Java search provider (default DuckDuckGo) and must contain `%s`. Set `VITE_API_TARGET` to change the development proxy target, or `CACHEBROWSE_API_PORT` to change the Java listener port.

Cloudflare D1 remains optional and uses `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN`, and `D1_DATABASE_ID`; Firebase email/password auth uses `FIREBASE_API_KEY`. Values may come from process environment or the repository-root `.env`. Never commit that file or print secret values. If D1 is not reachable, Java reports process-memory fallback mode.

### D1 and Firebase configuration

Copy `.env.example` to `.env` and fill it locally. The Java backend reads these variables from the process environment or the repository-root `.env`, including when launched from a child directory; process environment values take precedence. Keep `.env` private (for example, `chmod 600 .env`), and rotate any credential that has been pasted into chat or committed.

- Find the Account ID in the Cloudflare dashboard's account overview. Find the D1 database ID on that database's details page under Workers & Pages → D1.
- Create a Cloudflare API token scoped to the intended account with D1 read/write permissions. The backend calls `POST https://api.cloudflare.com/client/v4/accounts/{account_id}/d1/database/{database_id}/query` using a Bearer token.
- `FIREBASE_API_KEY` is the Firebase project's Web API key used by Identity Toolkit email/password sign-in. Enable Email/Password in Firebase Authentication and restrict the key to the required Firebase Identity Toolkit APIs and authorized app domains where supported. Firebase login is independent of D1 persistence.
- Start Java from the repository root with `mvn -q exec:java`. The startup diagnostic checks `SELECT 1`, then applies idempotent `CREATE TABLE IF NOT EXISTS` schema statements. The React Settings page reports the precise sanitized D1 issue and offers **Check D1 connection** to retry the current process configuration and schema initialization. Restart Java after editing `.env`; configuration is read at process startup.
- `missing_credentials`, `invalid_credentials`, `insufficient_permissions`, `account_or_database_not_found`, `dns_error`, `tls_error`, `request_timeout`, `sql_error`, and `schema_initialization_failed` indicate distinct failure classes. Do not treat the generic “configuration complete” log as proof of connectivity; only `persistenceAvailable: true` after the connection probe and schema initialization means D1 persistence is active.

The `.env.example` file contains placeholders only. Never put real credentials in source control or bug reports.

## Browser runtime and limits

The embedded engine is the Electron-bundled Chromium runtime using one persistent, protection-enabled session and one `WebContentsView` per tab. React draws browser chrome; Java continues to provide search resolution, history, bookmarks, settings, and statistics. The preload exposes a narrow validated IPC surface, with context isolation, sandboxing, Node integration disabled for pages, and HTTPS/HTTP-only navigation. Website requests opened in this runtime are subject to CacheBrowse's Chromium filter; websites opened in another browser are not. As with other filter engines, this does not guarantee removal of every YouTube ad. Vite-only mode is UI-only and displays a diagnostic instead of opening destinations externally.

## Build and test

```sh
# Backend tests (does not start the API server)
mvn -q test

# Frontend
cd frontend
npm test
npm run build
```

Older project-design notes below describe the original JavaFX browser exercise and are historical; they are not the current local-web startup instructions.

### Browser engine smoke test

```sh
cd frontend
CACHEBROWSE_REMOTE_DEBUGGING_PORT=9222 npm run dev
# In another frontend terminal, once CacheBrowse has started:
npm run smoke:desktop
```

The smoke test visits real websites and exercises navigation/tabs and an actual blocked request. Disable/replace Cloudflare credentials before running it if necessary; set `CACHEBROWSE_DISABLE_D1=true CACHEBROWSE_DISABLE_FIREBASE=true` for the desktop launch to keep this test isolated from configured account services.

The remainder of this README contains original course-project/design notes. Where its older JavaFX diagrams or feature descriptions conflict with the current runtime section above, the current runtime section is authoritative.

CacheBrowse began as a lightweight educational Java/JavaFX project and is being transitioned to the desktop runtime described above.

> **Note:** CacheBrowse is an educational/course project. It is not intended to replace Chrome, Firefox, Brave, or other production browsers.

---

## 📌 Project Overview

The main objective of CacheBrowse is to demonstrate how repeated web requests can be optimized using a custom **LRU (Least Recently Used) cache with TTL (Time To Live)** while also providing basic ad/tracker blocking and browser management features.

The project combines:

- Java OOP
- Data Structures and Algorithms
- Computer Networks
- Multithreading
- HTTP/HTTPS networking
- JavaFX
- LRU caching
- TTL expiration
- Ad blocking
- Tracker blocking
- Cloudflare D1 Database
- Firebase / Session Authentication
- Browser history
- Bookmarks
- Performance statistics
- Git-based team development

---

## 🎯 Problem Statement

When a browser repeatedly requests the same resources from the Internet, unnecessary network requests can increase:

- Network usage
- Response time
- Bandwidth consumption
- Number of server requests

CacheBrowse demonstrates how a custom cache can store previously received responses and return them on subsequent requests.

The application also demonstrates basic ad/tracker blocking and provides statistics showing how the cache and network are performing.

---

## 💡 Main Concept

```text
User
 ↓
CacheBrowse JavaFX UI
 ↓
Browser Controller
 ↓
Browser Service
 ↓
Ad / Tracker Check
 ↓
Cache Check
 ├─────────────────┐
 │                 │
CACHE HIT       CACHE MISS
 │                 │
 ↓                 ↓
Cached Data    Internet Request
 │                 │
 │                 ↓
 │            Receive Response
 │                 │
 │                 ↓
 │            Store in Cache
 │                 │
 └────────→ Return Response
                  ↓
               Browser
                  ↓
              Statistics
                  ↓
               History
```

---

# 🚀 Features

## Browser

- URL/search bar
- URL navigation
- Search queries
- Back
- Forward
- Refresh
- Home
- Multiple tabs
- New tab
- Close tab
- Page navigation

The search-engine provider does not need to be displayed in the main browser toolbar.

---

## 🔎 Smart Search Bar

The search bar accepts either a URL or a search query.

### URL

```text
https://example.com
```

### Search query

```text
Java LRU Cache
```

The backend determines whether the input is a URL or a search query.

Search queries are forwarded to the configured search provider.

---

# ⚡ Custom LRU Cache

Caching is one of the main technical components of CacheBrowse.

The implementation uses:

```text
HashMap + Doubly Linked List
```

This provides approximately O(1) operations for:

```text
get()
put()
remove()
```

### Why this structure?

The `HashMap` provides fast lookup, while the doubly linked list maintains the usage order required by the LRU algorithm.

---

## LRU Example

Suppose:

```text
Cache Capacity = 3

A
B
C
```

If `A` is accessed:

```text
B
C
A
```

If `D` is inserted:

```text
C
A
D
```

`B` is removed because it is the least recently used item.

---

# ⏱️ TTL — Time To Live

Each cache entry can have a TTL.

Example:

```text
TTL = 5 minutes
```

A cached response is valid until its expiration time.

```text
createdAt + ttlMillis = expirationTime
```

After expiration, the cache entry is removed or invalidated and the next request becomes a cache miss.

---

# 🗃️ Cache Operations

The backend supports:

- Create cache entry
- Read cache entry
- Update cache entry
- Delete cache entry
- Clear cache
- Change cache capacity
- Check expiration
- Remove expired entries

The UI should expose appropriate cache controls through Settings and Dashboard.

---

# 🔄 Request Flow

### First request

```text
User
 ↓
Request URL
 ↓
Cache Lookup
 ↓
CACHE MISS
 ↓
Internet
 ↓
Response
 ↓
Store Response in Cache
 ↓
Display
```

### Second request

```text
User
 ↓
Same URL
 ↓
Cache Lookup
 ↓
CACHE HIT
 ↓
Return Cached Response
```

This demonstrates how caching can reduce repeated network requests.

---

# 📊 Cache Statistics

The application tracks:

- Total Requests
- Cache Hits
- Cache Misses
- Hit Ratio
- Cache Evictions
- Expired Entries
- Cache Size
- Cache Capacity
- Network Requests
- Response Time
- Ads Blocked
- Trackers Blocked

### Hit Ratio

```text
Hit Ratio =
Cache Hits / (Cache Hits + Cache Misses) × 100
```

If there are no cache hits or misses:

```text
Hit Ratio = 0%
```

Statistics must be generated from actual operations. They must not be hardcoded.

---

# 🚫 Ad Blocker

CacheBrowse includes a basic domain/URL-based ad blocker.

Example patterns:

```text
ads
advertising
doubleclick
```

Supported operations:

```text
isBlocked()
addBlockedDomain()
removeBlockedDomain()
getBlockedDomains()
clearBlockedDomains()
setEnabled()
```

The blocker can be enabled or disabled from Settings.

---

# 🕵️ Tracker Blocker

Tracker blocking is separate from ad blocking.

Example patterns/categories:

```text
tracking
analytics
telemetry
```

Supported controls:

```text
Tracker Blocker ON
Tracker Blocker OFF
```

The dashboard tracks the number of blocked trackers separately.

---

# 🌐 Network Layer

The project uses Java 21 HTTP networking:

```java
java.net.http.HttpClient
java.net.http.HttpRequest
java.net.http.HttpResponse
```

The network layer should handle:

- HTTP requests
- HTTPS requests
- Redirects
- Timeouts
- HTTP status codes
- Content types
- Response bodies
- Network errors

Network operations must not block the JavaFX Application Thread.

---

# 🔌 Proxy Server

The project may include:

```text
ProxyServer
ClientHandler
RequestHandler
ResponseHandler
```

Conceptually:

```text
Client
 ↓
Proxy Server
 ↓
Request Handler
 ↓
Ad / Tracker Check
 ↓
Cache
 ↓
Internet
 ↓
Response
 ↓
Client
```

Concurrent clients can be handled using:

```text
ServerSocket
ExecutorService
```

### Important limitation

A custom Java proxy does not automatically make JavaFX `WebView` route all traffic through it.

CacheBrowse should not claim full browser-level proxy interception unless it is actually implemented.

---

# 🧵 Multithreading

Slow operations must run outside the JavaFX Application Thread.

Possible mechanisms:

```text
ExecutorService
CompletableFuture
JavaFX Task
Platform.runLater()
```

Operations suitable for background execution include:

- Network requests
- Database operations
- Authentication
- Proxy connections
- Expensive cache maintenance

Statistics must be thread-safe.

---

# 🗄️ Database

The persistent database is:

**Cloudflare D1 (SQLite-compatible via HTTP API)**

Tables:

```text
users
history
bookmarks
browser_settings
cache_metadata
statistics
```

Architecture:

```text
Browser Service
      ↓
Services
      ↓
Repositories
      ↓
Database Manager / D1Client
      ↓
Cloudflare D1 REST API
```

Repositories include:

```text
HistoryRepository
BookmarkRepository
SettingsRepository
StatisticsRepository
```

---

# 🔐 Authentication

Authentication can use:

**Firebase Authentication**

Supported functionality:

- Register
- Login
- Logout
- Current user
- Session state

Passwords must not be stored manually or in plaintext.

A `UserSession` can maintain non-sensitive information such as:

```text
User ID
Email
Login State
```

---

# 📜 History

History records can contain:

```text
URL
Page Title
Timestamp
Status
```

Operations:

```text
Add History
Get History
Delete History
Clear History
```

History should use real backend/database data rather than placeholder entries.

---

# 🔖 Bookmarks

Bookmarks support:

- Add
- Read
- Update
- Delete

Example:

```text
Title: Google
URL: https://google.com
```

Bookmarks can be persisted in PostgreSQL.

---

# ⚙️ Settings

Possible settings include:

```text
Cache Enabled
Cache Capacity
Cache TTL
Ad Blocker Enabled
Tracker Blocker Enabled
Home Page
Search Provider
Network Timeout
```

Settings must affect actual backend behavior.

For example:

```text
Cache OFF
```

should bypass cache reads and writes.

---

# 📈 Performance Dashboard

The dashboard displays real-time or refreshed backend statistics.

Example metrics:

```text
Total Requests
Cache Hits
Cache Misses
Hit Ratio
Ads Blocked
Trackers Blocked
Network Requests
Average Response Time
Cache Size
Cache Capacity
Cache Evictions
Expired Entries
```

Example values in this README are illustrative only. The application must calculate values from real operations.

---

# ⏱️ Response Time

Request performance can be measured using:

```java
long start = System.nanoTime();

// request

long end = System.nanoTime();
```

The application can calculate:

- Cache response time
- Network response time
- Average response time

---

# 🏗️ Architecture

```text
                    ┌─────────────────────┐
                    │      JavaFX UI      │
                    │ Browser / Dashboard │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │    Controllers      │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │      Services       │
                    └──────┬────────┬─────┘
                           │        │
                           ▼        ▼
                    ┌──────────┐ ┌──────────┐
                    │  Cache   │ │ Network  │
                    │  Layer   │ │  Layer   │
                    └──────────┘ └────┬─────┘
                                      │
                                      ▼
                                   Internet

                    Services
                       │
                ┌──────┴──────┐
                ▼             ▼
          Repositories   Authentication
                │             │
                ▼             ▼
          Cloudflare D1    Firebase
```

---

# 📁 Project Structure

```text
CacheBrowse/
│
├── pom.xml
├── README.md
├── .gitignore
├── .env.example
│
└── src/
    └── main/
        ├── java/
        │   └── com/cachebrowse/
        │
        │       ├── Main.java
        │       │
        │       ├── ui/
        │       │   ├── BrowserApplication.java
        │       │   ├── BrowserController.java
        │       │   ├── DashboardController.java
        │       │   └── SettingsController.java
        │       │
        │       ├── service/
        │       │   ├── BrowserService.java
        │       │   ├── CacheService.java
        │       │   ├── NetworkService.java
        │       │   ├── HistoryService.java
        │       │   ├── BookmarkService.java
        │       │   ├── StatisticsService.java
        │       │   ├── SettingsService.java
        │       │   └── AuthenticationService.java
        │       │
        │       ├── cache/
        │       │   ├── Cache.java
        │       │   ├── CacheEntry.java
        │       │   ├── LRUCache.java
        │       │   └── CacheManager.java
        │       │
        │       ├── network/
        │       │   ├── HttpClient.java
        │       │   ├── HttpRequest.java
        │       │   └── HttpResponse.java
        │       │
        │       ├── proxy/
        │       │   ├── ProxyServer.java
        │       │   ├── ClientHandler.java
        │       │   ├── RequestHandler.java
        │       │   └── ResponseHandler.java
        │       │
        │       ├── adblock/
        │       │   ├── AdBlocker.java
        │       │   ├── BlockList.java
        │       │   └── TrackerBlocker.java
        │       │
        │       ├── database/
        │       │   ├── DatabaseManager.java
        │       │   ├── HistoryRepository.java
        │       │   ├── BookmarkRepository.java
        │       │   ├── SettingsRepository.java
        │       │   └── StatisticsRepository.java
        │       │
        │       ├── auth/
        │       │   ├── AuthenticationService.java
        │       │   └── UserSession.java
        │       │
        │       ├── history/
        │       │   └── HistoryManager.java
        │       │
        │       ├── bookmark/
        │       │   └── BookmarkManager.java
        │       │
        │       ├── statistics/
        │       │   └── CacheStatistics.java
        │       │
        │       └── model/
        │           ├── BrowserSettings.java
        │           ├── HistoryEntry.java
        │           ├── Bookmark.java
        │           ├── User.java
        │           └── BrowserResponse.java
        │
        └── resources/
            └── styles.css
```

> The actual existing project structure should be treated as authoritative. New classes should only be added when needed, and duplicate implementations should be avoided.

---

# 👥 Team Responsibilities

The project is divided among four team members.

## Member 1 — Backend + API / Networking

### Responsibility

```text
BrowserService
NetworkService
HTTP Requests
Proxy
Request Pipeline
Navigation Backend
```

### Tasks

- Java HTTP client
- URL processing
- Search handling
- Network requests
- Response handling
- Proxy server
- Multithreading
- Backend integration with BrowserController

### Main packages

```text
service/
network/
proxy/
```

---

## Member 2 — Database + Algorithm / Cache

### Responsibility

```text
LRU Cache
TTL
Cache Manager
Statistics
Database
Persistence
```

### Tasks

- LRU implementation
- HashMap
- Doubly Linked List
- Cache CRUD
- TTL
- Cache statistics
- Cloudflare D1
- History repository
- Bookmark repository
- Settings repository

### Main packages

```text
cache/
database/
statistics/
```

### DSA questions to understand

- Why HashMap?
- Why Doubly Linked List?
- Why O(1)?
- How does LRU eviction work?
- How does TTL differ from LRU?

---

## Member 3 — UI + Documentation

### Responsibility

```text
JavaFX UI
Dashboard
Settings
Browser Interface
Documentation
```

### Tasks

- Browser UI
- Tabs
- Search bar
- Navigation buttons
- Dashboard
- Settings
- Login UI
- History UI
- Bookmark UI
- CSS
- Documentation
- Screenshots
- PPT
- Project report

### Main packages

```text
ui/
resources/
```

---

## Member 4 — Testing / QA / Integration

### Responsibility

```text
Testing
Integration
Bug Fixing
Validation
```

### Tasks

- Unit testing
- Integration testing
- Cache testing
- Network testing
- UI testing
- Database testing
- Authentication testing
- Ad blocker testing
- Tracker blocker testing
- Performance testing
- Final integration

### Test cases

```text
Cache HIT
Cache MISS
Cache eviction
TTL expiration
Ad blocking
Tracker blocking
Network failure
Database failure
Login failure
Logout
Bookmark
History
Settings
Dashboard statistics
```

---

# 🔀 Git Workflow

Recommended branches:

```text
main
develop
feature/backend
feature/cache-db
feature/ui-docs
feature/testing
```

Create a feature branch:

```bash
git checkout -b feature/cache-db
```

Commit:

```bash
git add .
git commit -m "Implement LRU cache and TTL"
```

Push:

```bash
git push origin feature/cache-db
```

Then create a Pull Request into:

```text
develop
```

After integration and testing:

```text
develop → main
```

---

# 🔒 Security

Never commit:

- API keys
- Database tokens
- Firebase secrets/configuration containing sensitive credentials
- User passwords
- Access tokens

Use environment variables.

Example `.env.example`:

```env
CLOUDFLARE_ACCOUNT_ID=
CLOUDFLARE_API_TOKEN=
D1_DATABASE_ID=

FIREBASE_API_KEY=
FIREBASE_AUTH_DOMAIN=
FIREBASE_PROJECT_ID=
```

The real `.env` should remain local and should be included in `.gitignore`.

---

# 🛡️ Security Limitations

CacheBrowse should not implement:

- TLS MITM
- HTTPS decryption
- Password interception
- Credential interception
- CAPTCHA solving
- Anti-bot bypass
- Certificate bypass

If an external website displays a CAPTCHA or anti-bot page, the application should not attempt to bypass it.

---

# 💻 Requirements

Current project target:

```text
Java 21
JavaFX 21
Maven
```

Check your installation:

```bash
java -version
javac -version
mvn -version
```

---

# ▶️ Running the Project

From the project root:

```bash
mvn clean compile
```

Run tests:

```bash
mvn test
```

Run the JavaFX application:

```bash
mvn javafx:run
```

---

# 🧪 Testing LRU

Example:

```text
Capacity = 3

A → PUT
B → PUT
C → PUT

A → GET

D → PUT
```

Expected:

```text
B is evicted
```

because `B` is the least recently used item.

---

# 🧪 Testing TTL

Example:

```text
TTL = 5 seconds
```

Insert:

```text
A
```

Immediately:

```text
GET A → HIT
```

After 5+ seconds:

```text
GET A → MISS
```

The expired entry should be removed or invalidated.

---

# 🧪 Testing Ad Blocking

Example request:

```text
https://example-ad-domain.com/banner.js
```

If it matches the block list:

```text
BLOCKED
```

The statistics should increment:

```text
adsBlocked++
```

If the blocker is disabled:

```text
ALLOW
```

---

# 🧪 Testing Dashboard

Perform:

```text
Request A
Request A again
Request B
Request C
```

The dashboard should reflect the actual:

```text
Total Requests
Cache Hits
Cache Misses
Network Requests
Hit Ratio
Response Time
```

No manually entered statistics should be used.

---

# 🎬 Final Demonstration

A suggested final demonstration:

### 1. Launch CacheBrowse

```text
mvn javafx:run
```

### 2. Open a URL

First request should demonstrate:

```text
CACHE MISS
```

### 3. Open/request the same resource again

Demonstrate:

```text
CACHE HIT
```

### 4. Open Dashboard

Show:

- Requests
- Hits
- Misses
- Hit Ratio
- Response Time
- Cache Usage

### 5. Enable Ad Blocker

Demonstrate a matching blocked request.

### 6. Enable Tracker Blocker

Demonstrate tracker blocking.

### 7. Open Settings

Demonstrate:

- Cache capacity
- TTL
- Ad blocker
- Tracker blocker

### 8. Open History

Show actual navigation records.

### 9. Open Bookmarks

Show saved bookmarks.

### 10. Authentication / Database

Demonstrate login and persistent data if the external services have been configured.

---

# 📚 Academic Concepts Demonstrated

## OOP

- Encapsulation
- Inheritance
- Polymorphism
- Abstraction
- Classes and Objects
- Interfaces

## DSA

- HashMap
- Doubly Linked List
- LRU
- O(1) operations

## Computer Networks

- HTTP
- HTTPS
- Client-server architecture
- Proxy
- Request/response
- Caching
- Network latency

## Multithreading

- ExecutorService
- Concurrent requests
- Asynchronous operations
- Thread-safe statistics

## Database

- PostgreSQL
- CRUD
- Relationships
- Prepared statements
- Persistence

## Software Engineering

- Layered architecture
- MVC-style separation
- Services
- Repositories
- Testing
- Git

---

# 🏁 Project Goal

The completed CacheBrowse system should integrate:

```text
                    CACHEBROWSE
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
       Browser         Cache         Security
          │              │              │
          │              │         ┌────┴────┐
          │              │         │         │
          │              │        Ads     Trackers
          │              │
          └──────┬───────┘
                 │
                 ▼
              Network
                 │
                 ▼
              Internet
                 │
                 ▼
             Statistics
                 │
           ┌─────┴─────┐
           ▼           ▼
       Database    Dashboard
```

## Key Team Principle

The project should not be treated as four unrelated mini-projects.

Each team member owns a module, but all modules must integrate through the same:

- request pipeline
- cache system
- statistics system
- service layer
- data models
- Git workflow

The final result should be **one working CacheBrowse application**.

---

## 📄 Project Information

**Project:** CacheBrowse – Java Smart Caching & Ad-Blocking Browser  
**Platform:** Desktop  
**Language:** Java 21  
**UI:** JavaFX  
**Database:** Cloudflare D1  
**Authentication:** Firebase / Session Authentication  
**Cache:** Custom LRU + TTL  
**Networking:** Java HTTP Client  
**Testing:** JUnit / integration testing  
**Build:** Maven  
**Version Control:** Git/GitHub

---

## 👨‍💻 Team

| Member | Responsibility |
|---|---|
| Member 1 | Backend + API / Networking |
| Member 2 | Database + Algorithm / Cache |
| Member 3 | UI + Documentation |
| Member 4 | Testing / QA / Integration |

---

## 📌 Status

The project is under active development.

The architecture and feature list describe the intended system with Cloudflare D1 database and Firebase/Session authentication.

## Firebase authentication and private browser data

The React client uses Firebase Authentication for email/password and Google sign-in. Public web configuration is read from the root `.env` by Vite and explicitly limited to the Firebase web config keys; never place an Admin SDK service-account JSON or Cloudflare token in frontend variables. Enable **Email/Password** and **Google** under Firebase Console → Authentication → Sign-in method, and add `localhost` (and `127.0.0.1` if used) under Authentication → Settings → Authorized domains.

The Java API verifies each Firebase ID token with Firebase Admin (`verifyIdToken(token, true)`). The web API key and Firebase project ID are public client configuration; they are not server credentials. **The Java verifier additionally requires a service-account credential or supported Google Application Default Credentials (ADC).** The project ID alone, Firebase web API key, and Cloudflare D1 token cannot verify Firebase ID tokens. If the Java backend has no server credential, protected APIs return HTTP 503 and do not accept the request.

For local Linux development, download a service-account JSON for the same Firebase project as `FIREBASE_PROJECT_ID` from Firebase Console → Project settings → Service accounts. Keep it outside this repository and restrict its file permissions:

```bash
mkdir -p "$HOME/.config/cachebrowse"
install -m 600 /path/to/downloaded-service-account.json "$HOME/.config/cachebrowse/firebase-service-account.json"
```

Set this path in the root `.env` (the file is Git-ignored), then restart the Java backend:

```dotenv
GOOGLE_APPLICATION_CREDENTIALS=/home/your-user/.config/cachebrowse/firebase-service-account.json
```

The backend resolves a relative credential path from the project root, checks that the file is readable, and rejects a service-account project mismatch. Alternatively, configure supported ADC for the Java process. Never put this JSON in `frontend/.env`, a `VITE_` variable, source control, or browser code. `.env.example` contains only a path placeholder. D1 connectivity is independent and still requires its own Cloudflare configuration.

History, bookmarks, and settings endpoints require a Firebase ID token and use the UID from the server-verified token as the D1 owner. Request-supplied UIDs are not used to select another user's records. D1 may remain in explicit fallback mode if its own credentials or connectivity are unavailable.
