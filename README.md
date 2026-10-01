# CacheBrowse

**CacheBrowse** is a lightweight educational browser written in Java that demonstrates
the core concepts of computer networking, caching, ad‑blocking, multithreading and
modern UI development with JavaFX.  It consists of two main parts:

1. **JavaFX Browser UI** – a very small front‑end that shows web pages inside a
   `WebView`.
2. **Local HTTP proxy** – sits between the browser and the real internet, parses
   requests, decides whether to serve from cache or fetch over the network,
   blocks ads/tracking domains and keeps detailed statistics.

This repository contains only *Stage 1* – a skeleton project that compiles and
runs a JavaFX window.  The proxy, cache, ad‑blocker, statistics etc. will be added
in the following stages described in the original prompt.

## Prerequisites

- **Java 21** or newer (JDK)
- **Maven 3.9+** – used to compile and run the project
- A recent desktop environment that supports JavaFX (Linux/Windows/macOS).  On
  Linux you may need to install platform specific JARs; see the [OpenJFX
  documentation](https://openjfx.io/openjfx-docs/) for details.

## Building and Running

```bash
mvn clean javafx:run
```

The command cleans previous builds, compiles all sources and launches the JavaFX
application.  A window titled **CacheBrowse** will appear showing `https://www.example.com`.

### Troubleshooting on Linux

If you encounter an error such as:

```
Exception in thread "main" java.lang.RuntimeException: JavaFX runtime components are missing, and are required to run this application
```

you probably need the platform specific JavaFX libraries.  For example on Debian/Ubuntu:

```bash
sudo apt-get install libopenjfx-java
```

Or add a classifier to the Maven dependency, e.g. `org.openjfx:javafx-controls:21:linux`.

## Next Steps

The following stages are planned (see the full specification in the original prompt):

1. **HTTP client** – use `java.net.http.HttpClient` for outbound requests.
2. **Local proxy server** – implement a multithreaded HTTP proxy on port 8080.
3. **Request parsing \u0026 response handling** – build a simple HTTP parser.
4. **Cache system (LRU + TTL)** – custom cache with eviction policy.
5. **Ad/Tracker blocking** – domain and URL pattern filtering.
6. **Statistics \u0026 dashboard** – real‑time performance metrics.
7. **History / Bookmarks** – persistent storage of browsing data.
8. **Settings UI** – allow the user to change cache size, TTL, ad‑blocking mode etc.

\u003e The goal is not to replace a full browser but to provide an educational sandbox that shows how the pieces fit together.

---

[License](/LICENSE)
---