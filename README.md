# CacheBrowse

**Java Smart Caching & Ad-Blocking Browser**

CacheBrowse is a lightweight educational desktop browser-like application built with Java and JavaFX. It demonstrates how browser requests, custom caching, networking, ad/tracker blocking, databases, authentication, multithreading, and performance monitoring can work together.

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
- PostgreSQL/Supabase
- Firebase Authentication
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

The planned persistent database is:

**Supabase PostgreSQL**

Possible tables:

```text
profiles
history
bookmarks
browser_settings
```

Optional:

```text
cache_statistics
```

Architecture:

```text
Browser Service
      ↓
Services
      ↓
Repositories
      ↓
Database Manager
      ↓
Supabase PostgreSQL
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
        Supabase/PostgreSQL  Firebase
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
- PostgreSQL/Supabase
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
- Database passwords
- Firebase secrets/configuration containing sensitive credentials
- User passwords
- Access tokens

Use environment variables.

Example `.env.example`:

```env
SUPABASE_DB_URL=
SUPABASE_DB_USER=
SUPABASE_DB_PASSWORD=

FIREBASE_API_KEY=
FIREBASE_AUTH_DOMAIN=
FIREBASE_PROJECT_ID=
FIREBASE_STORAGE_BUCKET=
FIREBASE_MESSAGING_SENDER_ID=
FIREBASE_APP_ID=
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
**Database:** Supabase PostgreSQL  
**Authentication:** Firebase Authentication  
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

The architecture and feature list describe the intended final system. Individual backend integrations such as Supabase and Firebase require the team's project credentials/configuration before they can operate against the team's live services.
