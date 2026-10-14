/**
 * CacheBrowse API client
 * All communication goes through the Java backend at /api (proxied by Vite → localhost:8080).
 *
 * SECURITY: No secrets are stored here. Firebase credentials, Cloudflare D1 tokens,
 * and all sensitive config live exclusively in the Java backend /.env file.
 */
import { auth } from './firebase.js';

const BASE = window.cacheBrowse?.apiBaseUrl || '/api';

/**
 * Core request helper.
 * - Throws BackendOfflineError when the Java backend is not running (ECONNREFUSED → 502/503).
 * - Throws ApiError with the HTTP status + body for all other non-2xx responses.
 */
async function request(path, options = {}) {
  const token = auth?.currentUser ? await auth.currentUser.getIdToken() : null;
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (token) headers.Authorization = `Bearer ${token}`;
  let res;
  if (window.cacheBrowse?.requestApi) {
    let result;
    try {
      result = await window.cacheBrowse.requestApi(`${BASE}${path}`, { ...options, headers });
    } catch {
      throw new BackendOfflineError();
    }
    if (!result.ok) {
      if ([502, 504].includes(result.status)) throw new BackendOfflineError();
      throw new ApiError(result.status, typeof result.body === 'string' ? result.body : JSON.stringify(result.body));
    }
    return result.body;
  }

  try {
    res = await fetch(`${BASE}${path}`, {
      ...options,
      headers,
    });
  } catch (networkErr) {
    // fetch() itself threw — this means the Vite proxy could not connect to Java
    throw new BackendOfflineError();
  }

  // Vite proxy returns 502/504 when Java backend is down
  if (res.status === 502 || res.status === 504) {
    throw new BackendOfflineError();
  }

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new ApiError(res.status, text || `HTTP ${res.status}`);
  }

  const ct = res.headers.get('Content-Type') || '';
  if (ct.includes('application/json')) return res.json();
  return res.text();
}

/** Thrown when the Java backend is not reachable. */
export class BackendOfflineError extends Error {
  constructor() { super('Backend offline'); this.name = 'BackendOfflineError'; }
}

/** Thrown for non-2xx HTTP responses from the backend. */
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.name  = 'ApiError';
    this.status = status;
  }
}

/** Returns true if the error means the backend is simply not started yet. */
export function isOfflineError(err) {
  return err instanceof BackendOfflineError;
}

// ── Auth ──────────────────────────────────────────────────────────────────────
export const authApi = {
  getUser:  ()               => request('/auth/user'),
};

export const healthApi = {
  get: () => request('/health'),
  checkDatabase: () => request('/database/check', { method: 'POST' }),
};

// ── History ───────────────────────────────────────────────────────────────────
export const historyApi = {
  getAll: (limit = 100) => request(`/history?limit=${limit}`),
  visit: (url, title) => request('/history/visit', { method: 'POST', body: JSON.stringify({ url, title }) }),
  delete: (id)          => request(`/history/${id}`,  { method: 'DELETE' }),
  clear:  ()            => request('/history',         { method: 'DELETE' }),
};

// ── Bookmarks ─────────────────────────────────────────────────────────────────
export const bookmarkApi = {
  getAll:       ()          => request('/bookmarks'),
  add:          (url, title)=> request('/bookmarks',       { method: 'POST',   body: JSON.stringify({ url, title }) }),
  update:       (id, url, title) => request('/bookmarks',  { method: 'PUT', body: JSON.stringify({ id, url, title }) }),
  remove:       (id)        => request(`/bookmarks/${id}`, { method: 'DELETE' }),
  clear:        ()          => request('/bookmarks',       { method: 'DELETE' }),
  isBookmarked: (url)       => request(`/bookmarks/check?url=${encodeURIComponent(url)}`),
};

// ── Statistics ────────────────────────────────────────────────────────────────
export const statsApi = {
  get:   () => request('/statistics'),
  reset: () => request('/statistics/reset', { method: 'POST' }),
};

// ── Settings ──────────────────────────────────────────────────────────────────
export const settingsApi = {
  get:    ()         => request('/settings'),
  update: (settings) => request('/settings', { method: 'PUT', body: JSON.stringify(settings) }),
};

// ── Browser navigation ────────────────────────────────────────────────────────
export const browserApi = {
  getStatus: ()      => request('/browser/status'),
};

// Java resolves the address/search input. In local web mode the resulting site
// opens in the host browser because this page has no privileged renderer.
export const searchApi = {
  resolve: query => request('/search', {
    method: 'POST',
    body: JSON.stringify({ query }),
  }),
};

// ── Cache ─────────────────────────────────────────────────────────────────────
export const cacheApi = {
  getStatus: () => request('/cache/status'),
  getStatistics: () => request('/cache/statistics'),
  clear: () => request('/cache/clear', { method: 'POST' }),
  cleanup: () => request('/cache/cleanup', { method: 'POST' }),
};

// ── Protection toggles ────────────────────────────────────────────────────────
export const protectionApi = {
  setAdBlock:      (enabled) => request('/protection/adblock', { method: 'PUT', body: JSON.stringify({ enabled }) }),
  setTrackerBlock: (enabled) => request('/protection/tracker', { method: 'PUT', body: JSON.stringify({ enabled }) }),
  getStatus:       ()        => request('/protection/status'),
  getRules:        ()        => request('/protection/rules'),
  getExceptions:   ()        => request('/protection/allowlist'),
  getStatistics:   ()        => request('/protection/statistics'),
  addAllowlist:    domain    => request('/protection/allowlist', { method: 'POST', body: JSON.stringify({ domain }) }),
  removeAllowlist: domain    => request('/protection/allowlist', { method: 'DELETE', body: JSON.stringify({ domain }) }),
};
