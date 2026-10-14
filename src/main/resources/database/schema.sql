-- ==========================================================
-- CacheBrowse - Cloudflare D1 Database Schema
-- Compatible with SQLite / Cloudflare D1 HTTP API
--
-- IMPORTANT: Keep this file identical to database/schema.sql
-- DatabaseManager loads from the filesystem path first and
-- falls back to this classpath resource when running as a JAR.
-- ==========================================================

-- 1. Users Table
--    All child tables (history, bookmarks, browser_settings, statistics)
--    reference this table via FOREIGN KEY (user_id) REFERENCES users(id).
--    DatabaseManager.initSchema() immediately after schema creation.
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    firebase_uid TEXT UNIQUE,
    email TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_users_firebase_uid ON users(firebase_uid);

-- 2. Browsing History Table
CREATE TABLE IF NOT EXISTS history (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    url TEXT NOT NULL,
    title TEXT,
    visited_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_history_user_visited ON history(user_id, visited_at DESC);
CREATE INDEX IF NOT EXISTS idx_history_url ON history(url);

-- 3. Bookmarks Table
CREATE TABLE IF NOT EXISTS bookmarks (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    url TEXT NOT NULL,
    title TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, url)
);

CREATE INDEX IF NOT EXISTS idx_bookmarks_user ON bookmarks(user_id, created_at DESC);

-- 4. Browser Settings Table
CREATE TABLE IF NOT EXISTS browser_settings (
    user_id TEXT PRIMARY KEY,
    cache_enabled INTEGER NOT NULL DEFAULT 1,
    cache_capacity INTEGER NOT NULL DEFAULT 100,
    cache_ttl INTEGER NOT NULL DEFAULT 300000,
    ad_block_enabled INTEGER NOT NULL DEFAULT 1,
    tracker_block_enabled INTEGER NOT NULL DEFAULT 1,
    home_page TEXT NOT NULL DEFAULT '',
    default_search_engine TEXT NOT NULL DEFAULT 'https://html.duckduckgo.com/html/?q=%s',
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 5. Cache Metadata Table
CREATE TABLE IF NOT EXISTS cache_metadata (
    id TEXT PRIMARY KEY,
    url TEXT UNIQUE NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    expires_at TEXT NOT NULL,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    hit_count INTEGER NOT NULL DEFAULT 0,
    content_type TEXT DEFAULT 'text/html'
);

CREATE INDEX IF NOT EXISTS idx_cache_metadata_url ON cache_metadata(url);
CREATE INDEX IF NOT EXISTS idx_cache_metadata_expires ON cache_metadata(expires_at);

-- 6. Statistics Table
CREATE TABLE IF NOT EXISTS statistics (
    user_id TEXT PRIMARY KEY,
    total_requests INTEGER NOT NULL DEFAULT 0,
    cache_hits INTEGER NOT NULL DEFAULT 0,
    cache_misses INTEGER NOT NULL DEFAULT 0,
    ads_blocked INTEGER NOT NULL DEFAULT 0,
    trackers_blocked INTEGER NOT NULL DEFAULT 0,
    network_requests INTEGER NOT NULL DEFAULT 0,
    avg_response_time REAL NOT NULL DEFAULT 0.0,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 7. Protection Events Table (Optional Persistent Logs)
CREATE TABLE IF NOT EXISTS protection_events (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    session_id TEXT NOT NULL,
    tab_id TEXT,
    page_url TEXT,
    page_hostname TEXT,
    resource_url TEXT NOT NULL,
    resource_hostname TEXT NOT NULL,
    event_type TEXT NOT NULL,
    action TEXT NOT NULL,
    rule TEXT,
    reason TEXT,
    resource_type TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_protection_events_session ON protection_events(session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_protection_events_user ON protection_events(user_id, created_at DESC);

