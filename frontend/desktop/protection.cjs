const fs = require('node:fs/promises');
const path = require('node:path');
const {
  ElectronBlocker,
  NetworkFilter,
  fromElectronDetails,
  adsLists,
  adsAndTrackingLists,
} = require('@ghostery/adblocker-electron');

const FILTER_TTL_MS = 24 * 60 * 60 * 1000;
const RETRY_MS = 60 * 60 * 1000;
const FETCH_TIMEOUT_MS = 20_000;
const MAX_LIST_BYTES = 8 * 1024 * 1024;
const MAX_SNAPSHOT_BYTES = 100 * 1024 * 1024;
const FILTER_PACKAGE_VERSION = require('@ghostery/adblocker-electron/package.json').version;
const FILTER_STATE_FILE = 'filter-list-state.json';
const FILTER_FILES = { ads: 'ads.engine', trackers: 'trackers.engine' };
// Keep the maintained core subscriptions and their compatibility/exception
// rules. Omitting archived yearly snapshots avoids redundant network downloads.
const FILTER_AD_LISTS = adsLists.filter(url =>
  /\/easylist\/easylist\.txt$|peter-lowe\/serverlist\.txt$|ublock-origin\/(?:filters\.txt|quick-fixes\.txt|unbreak\.txt)$/.test(url));
const TRACKER_LISTS = adsAndTrackingLists.filter(url => !adsLists.includes(url));
const filterFetchCache = new Map();
const INJECTION_HANDLER = '@ghostery/adblocker/inject-cosmetic-filters';
const MUTATION_HANDLER = '@ghostery/adblocker/is-mutation-observer-enabled';

const FALLBACK_AD_FILTERS = [
  '||doubleclick.net^',
  '||googlesyndication.com^',
  '||googleadservices.com^',
  '||adservice.google.com^',
  '||adnxs.com^',
  '||amazon-adsystem.com^',
  '||criteo.com^',
  '||criteo.net^',
  '||taboola.com^',
  '||outbrain.com^',
  '||pubmatic.com^',
  '||openx.net^',
].join('\n');

const FALLBACK_TRACKER_FILTERS = [
  '||google-analytics.com^',
  '||hotjar.com^',
  '||segment.io^',
  '||mixpanel.com^',
  '||scorecardresearch.com^',
  '||quantserve.com^',
  '||clarity.ms^',
  '||amplitude.com^',
  '||sentry.io^',
].join('\n');

const ENGINE_CONFIG = {
  enableInMemoryCache: true,
  enableMutationObserver: true,
  enableOptimizations: true,
  guessRequestTypeFromUrl: true,
  loadCSPFilters: true,
  loadCosmeticFilters: true,
  loadNetworkFilters: true,
  loadPreprocessors: true,
};

const TRACKER_ENGINE_CONFIG = {
  enableInMemoryCache: true,
  enableOptimizations: true,
  guessRequestTypeFromUrl: true,
  loadCSPFilters: false,
  loadCosmeticFilters: false,
  loadNetworkFilters: true,
  loadPreprocessors: true,
};

function getHost(value) {
  try { return new URL(value).hostname.toLowerCase(); }
  catch { return ''; }
}

function isDomainOrSubdomain(host, domain) {
  return host === domain || host.endsWith(`.${domain}`);
}

function isYouTubeHost(host) {
  return ['youtube.com', 'youtube-nocookie.com', 'youtubei.googleapis.com']
    .some(domain => isDomainOrSubdomain(host, domain));
}

function isGoogleVideoHost(host) {
  return isDomainOrSubdomain(host, 'googlevideo.com');
}

function isAllowlisted(rules, sourceUrl) {
  const sourceHost = getHost(sourceUrl);
  if (!sourceHost) return false;
  return rules.some(rule => {
    if (typeof rule !== 'string') return false;
    const clean = rule.trim().toLowerCase().replace(/^\*\./, '');
    return clean && isDomainOrSubdomain(sourceHost, clean);
  });
}

function isProtectedPlaybackRequest(details) {
  const target = new URL(details.url);
  const targetHost = target.hostname.toLowerCase();
  const sourceUrl = details.initiatorOrigin || details.referrer || '';
  const sourceHost = getHost(sourceUrl);

  // YouTube serves both requested videos and some in-stream advertising from
  // googlevideo.com. The traffic cannot be safely distinguished at this layer.
  if (isGoogleVideoHost(targetHost)) return true;

  // Keep media initiated by YouTube, including MSE/DASH segments whose
  // Chromium resource type can vary between media and XHR.
  if (details.resourceType === 'media' && isYouTubeHost(sourceHost)) return true;

  // These player APIs carry the selected video's playback/session state.
  if ((isYouTubeHost(targetHost) || targetHost === 'youtubei.googleapis.com')
      && /^\/youtubei\/v1\/(?:player|next|browse)(?:\/|$)/.test(target.pathname)) return true;

  return false;
}

function makeFilterRequest(details) {
  const sourceUrl = details.initiatorOrigin || details.referrer || '';
  // Use the engine's Electron adapter so request IDs, tab IDs, referrers, and
  // resource types follow the filtering engine's supported mapping. Some
  // Chromium requests arrive as `other`; infer a type from the URL so
  // $script/$image/etc. rules don't silently miss those requests.
  const request = fromElectronDetails({ ...details, referrer: sourceUrl });
  if (request.type === 'other') request.guessTypeOfRequest();
  return request;
}

function normalizeCustomRule(rule) {
  if (typeof rule !== 'string') return null;
  const clean = rule.trim();
  if (!clean || clean.startsWith('#')) return null;
  if (clean.startsWith('||') || clean.startsWith('@@')) return clean;

  if (clean.startsWith('/')) return `*${clean}*`;
  try {
    const parsed = new URL(clean);
    return `||${parsed.hostname}${parsed.pathname === '/' ? '^' : `${parsed.pathname}*`}`;
  } catch { /* Accept a bare hostname or a path fragment below. */ }

  if (/^(?:\*\.)?[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/i.test(clean)) {
    return `||${clean.replace(/^\*\./, '')}^`;
  }
  if (clean.startsWith('*')) return clean;
  return `*${clean}*`;
}

function compileCustomRules(rules) {
  const compiled = [];
  for (const rule of Array.isArray(rules) ? rules : []) {
    const source = normalizeCustomRule(rule);
    if (!source) continue;
    try {
      const filter = NetworkFilter.parse(source);
      if (filter) compiled.push(filter);
    } catch { /* Invalid user rules are ignored; maintained lists still work. */ }
  }
  return compiled;
}

function matchCustomRules(filters, request) {
  let matchedBlock = null;
  for (const filter of filters) {
    if (!filter.match(request)) continue;
    if (filter.isException()) return null;
    matchedBlock ||= filter;
  }
  return matchedBlock;
}

function decideRequest({ details, protection, adsEngine, trackersEngine, fallbackAdsEngine, fallbackTrackersEngine, customAds, customTrackers }) {
  let target;
  try { target = new URL(details.url); }
  catch { return { action: 'allow', reason: 'malformed-url' }; }
  if (!['http:', 'https:'].includes(target.protocol) || details.resourceType === 'mainFrame') {
    return { action: 'allow', reason: 'navigation-or-non-http' };
  }

  const sourceUrl = details.initiatorOrigin || details.referrer || '';
  if (isAllowlisted(protection.allowlist, sourceUrl)) {
    return { action: 'allow', reason: 'site-allowlisted' };
  }
  if (isProtectedPlaybackRequest(details)) {
    return { action: 'allow', reason: 'youtube-playback-safety' };
  }

  const request = makeFilterRequest(details);
  if (protection.adBlock) {
    const listResult = adsEngine?.match(request);
    const fallbackResult = fallbackAdsEngine?.match(request);
    const customFilter = matchCustomRules(customAds, request);
    const result = listResult?.match ? listResult : fallbackResult?.match ? fallbackResult : null;
    if (result || customFilter) {
      const selected = result || { match: true, filter: customFilter, redirect: undefined };
      return { action: selected.redirect ? 'redirect' : 'block', category: 'ad', filter: selected.filter, redirect: selected.redirect };
    }
  }

  if (protection.trackerBlock) {
    const listResult = trackersEngine?.match(request);
    const fallbackResult = fallbackTrackersEngine?.match(request);
    const customFilter = matchCustomRules(customTrackers, request);
    const result = listResult?.match ? listResult : fallbackResult?.match ? fallbackResult : null;
    if (result || customFilter) {
      const selected = result || { match: true, filter: customFilter, redirect: undefined };
      return { action: selected.redirect ? 'redirect' : 'block', category: 'tracker', filter: selected.filter, redirect: selected.redirect };
    }
  }

  return { action: 'allow', reason: 'no-matching-filter' };
}

async function boundedFetch(url) {
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || parsed.hostname !== 'raw.githubusercontent.com') {
    throw new Error(`Rejected unexpected filter-list URL: ${parsed.hostname}`);
  }
  const response = await fetch(url, { signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
  if (!response.ok) throw new Error(`Filter-list server returned HTTP ${response.status}`);
  const body = await response.text();
  if (Buffer.byteLength(body, 'utf8') > MAX_LIST_BYTES) throw new Error('Filter list exceeded the size limit');
  return {
    text: async () => body,
    json: async () => JSON.parse(body),
    arrayBuffer: async () => {
      const bytes = Buffer.from(body, 'utf8');
      return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
    },
  };
}

async function sharedFilterFetch(url) {
  let pending = filterFetchCache.get(url);
  if (!pending) {
    pending = boundedFetch(url);
    filterFetchCache.set(url, pending);
  }
  try { return await pending; }
  catch (error) {
    if (filterFetchCache.get(url) === pending) filterFetchCache.delete(url);
    throw error;
  }
}

async function atomicWrite(file, data) {
  await fs.mkdir(path.dirname(file), { recursive: true, mode: 0o700 });
  const temporary = `${file}.${process.pid}.tmp`;
  await fs.writeFile(temporary, data, { mode: 0o600 });
  await fs.rename(temporary, file);
}

function engineRuleCount(engine) {
  return engine.getFilters().networkFilters.length;
}

class ChromiumProtection {
  constructor({ session, ipcMain, cacheDirectory, onBlocked, logger = console, debug = process.env.CACHEBROWSE_ADBLOCK_DEBUG === '1' }) {
    this.session = session;
    this.ipcMain = ipcMain;
    this.cacheDirectory = cacheDirectory;
    this.onBlocked = onBlocked;
    this.logger = logger;
    this.debug = debug;
    this.protection = { adBlock: true, trackerBlock: true, adRules: [], trackerRules: [], allowlist: [] };
    this.customAds = [];
    this.customTrackers = [];
    this.adsEngine = ElectronBlocker.parse(FALLBACK_AD_FILTERS, ENGINE_CONFIG);
    this.trackersEngine = ElectronBlocker.parse(FALLBACK_TRACKER_FILTERS, TRACKER_ENGINE_CONFIG);
    this.fallbackAdsEngine = ElectronBlocker.parse(FALLBACK_AD_FILTERS, ENGINE_CONFIG);
    this.fallbackTrackersEngine = ElectronBlocker.parse(FALLBACK_TRACKER_FILTERS, TRACKER_ENGINE_CONFIG);
    this.adSessionEnabled = false;
    this.requestHookInstalled = false;
    this.refreshTimer = null;
    this.refreshRunning = false;
    this.refreshHadFailure = false;
    this.filterState = { packageVersion: FILTER_PACKAGE_VERSION };
    this.blockedLogCount = 0;
    this.diagnosticLogCount = 0;
    this.cosmeticLogCount = 0;
  }

  async start(initialProtection = this.protection) {
    await this.loadSnapshots();
    this.setProtection(initialProtection);
    try {
      this.adsEngine.enableBlockingInSession(this.session);
      this.adSessionEnabled = true;
    } catch (error) {
      this.logger.warn(`[Protection] Cosmetic-filter hook unavailable; using Chromium network filtering only: ${error.message}`);
    }
    this.bindManagedHandlers();

    const needsAds = !this.snapshotLoaded?.ads || this.isStale('ads');
    const needsTrackers = !this.snapshotLoaded?.trackers || this.isStale('trackers');
    if (needsAds || needsTrackers) {
      // Warm the maintained lists before first navigation when the network is
      // responsive. Fallback rules remain active throughout; a slow/unavailable
      // list server cannot prevent the browser shell from starting.
      const refresh = this.refreshFilterLists();
      let warmupTimer;
      await Promise.race([refresh, new Promise(resolve => {
        warmupTimer = setTimeout(resolve, 4_000);
        warmupTimer.unref?.();
      })]);
      clearTimeout(warmupTimer);
    } else {
      this.scheduleFilterRefresh(FILTER_TTL_MS);
    }
    this.logger.info(`[Protection] Chromium session ready; filters=${engineRuleCount(this.adsEngine)} ad / ${engineRuleCount(this.trackersEngine)} tracker (cached/fallback until refresh).`);
  }

  setProtection(value) {
    this.protection = {
      adBlock: value?.adBlock === true,
      trackerBlock: value?.trackerBlock === true,
      adRules: Array.isArray(value?.adRules) ? [...value.adRules] : [],
      trackerRules: Array.isArray(value?.trackerRules) ? [...value.trackerRules] : [],
      allowlist: Array.isArray(value?.allowlist) ? [...value.allowlist] : [],
    };
    this.customAds = compileCustomRules(this.protection.adRules);
    this.customTrackers = compileCustomRules(this.protection.trackerRules);
  }

  evaluate(details) {
    return decideRequest({
      details,
      protection: this.protection,
      adsEngine: this.adsEngine,
      trackersEngine: this.trackersEngine,
      fallbackAdsEngine: this.fallbackAdsEngine,
      fallbackTrackersEngine: this.fallbackTrackersEngine,
      customAds: this.customAds,
      customTrackers: this.customTrackers,
    });
  }

  bindManagedHandlers() {
    // Electron supports only one listener per webRequest event. This is the
    // single policy pipeline for every WebContentsView sharing this session.
    this.session.webRequest.onBeforeRequest({ urls: ['<all_urls>'] }, (details, callback) => {
      let called = false;
      const finish = response => {
        if (called) return;
        called = true;
        callback(response);
      };
      let decision;
      try { decision = this.evaluate(details); }
      catch (error) {
        this.logger.warn(`[Protection] Filter evaluation failed open: ${error.message}`);
        finish({});
        return;
      }
      this.logDecision(details, decision);

      if (decision.action === 'redirect') {
        finish({ redirectURL: decision.redirect.dataUrl });
        this.reportBlocked(decision.category, details, decision.filter?.rawLine, 'redirected');
        return;
      }
      if (decision.action !== 'block') {
        finish({});
        return;
      }

      // Counters update only after Chromium cancels or redirects the request.
      finish({ cancel: true });
      this.reportBlocked(decision.category, details, decision.filter?.rawLine, 'canceled');
    });
    this.requestHookInstalled = true;

    this.session.webRequest.onHeadersReceived({ urls: ['<all_urls>'] }, (details, callback) => {
      if (!this.protection.adBlock || isAllowlisted(this.protection.allowlist, details.url)
          || isYouTubeHost(getHost(details.url))) {
        callback({});
        return;
      }
      try { this.adsEngine.onHeadersReceived(details, callback); }
      catch (error) {
        this.logger.warn(`[Protection] CSP filter failed open: ${error.message}`);
        callback({});
      }
    });

    if (this.adSessionEnabled) this.bindCosmeticHandlers();
  }

  bindCosmeticHandlers() {
    this.ipcMain.removeHandler(INJECTION_HANDLER);
    this.ipcMain.handle(INJECTION_HANDLER, async (event, url, message) => {
      const senderUrl = event.senderFrame?.url || '';
      let sameOrigin = false;
      try { sameOrigin = Boolean(senderUrl) && new URL(senderUrl).origin === new URL(url).origin; }
      catch { /* Reject malformed IPC origins. */ }
      if (!this.protection.adBlock || isAllowlisted(this.protection.allowlist, url)
          || event.sender.session !== this.session
          || !sameOrigin) return;
      let stylesQueued = 0;
      let scriptsQueued = 0;
      const safeSender = Object.create(event.sender);
      safeSender.insertCSS = (...args) => {
        stylesQueued += 1;
        return this.runCosmeticOperation(event.sender, 'insertCSS', args);
      };
      safeSender.executeJavaScript = (...args) => {
        scriptsQueued += 1;
        return this.runCosmeticOperation(event.sender, 'executeJavaScript', args);
      };
      try {
        const result = await this.adsEngine.onInjectCosmeticFilters({
          sender: safeSender,
          frameId: event.frameId,
          processId: event.processId,
        }, url, message);
        if (this.debug && isYouTubeHost(getHost(url)) && this.cosmeticLogCount < 300) {
          this.cosmeticLogCount += 1;
          this.logger.info(`[AdBlock] cosmetic filter pass host=${getHost(url)} styles=${stylesQueued} scripts=${scriptsQueued}`);
        }
        return result;
      } catch (error) {
        this.logger.warn(`[Protection] Cosmetic filter evaluation failed for ${getHost(url) || 'page'} (${error?.name || 'error'}).`);
        return undefined;
      }
    });

    this.ipcMain.removeHandler(MUTATION_HANDLER);
    this.ipcMain.handle(MUTATION_HANDLER, event => {
      const url = event.senderFrame?.url || '';
      return this.protection.adBlock && !isAllowlisted(this.protection.allowlist, url)
        && this.adsEngine.config.enableMutationObserver;
    });
  }

  reportBlocked(category, details, rule, outcome) {
    this.logBlocked(category, details, getHost(details.url), rule, outcome);
    Promise.resolve(this.onBlocked?.(category, details)).catch(error => {
      this.logger.warn(`[Protection] Could not report blocked request: ${error.message}`);
    });
  }

  getRuntimeStatus() {
    const lists = this.snapshotLoaded || { ads: false, trackers: false };
    const updatedAt = [this.filterState?.ads?.updatedAt, this.filterState?.trackers?.updatedAt]
      .filter(value => Number.isFinite(Number(value)) && Number(value) > 0)
      .map(Number);
    return {
      available: true,
      requestFilteringReady: this.requestHookInstalled,
      adBlockEnforcing: this.requestHookInstalled && this.protection.adBlock,
      trackerBlockEnforcing: this.requestHookInstalled && this.protection.trackerBlock,
      filterLists: {
        ads: lists.ads ? 'maintained' : 'fallback',
        trackers: lists.trackers ? 'maintained' : 'fallback',
        lastUpdated: updatedAt.length ? new Date(Math.min(...updatedAt)).toISOString() : null,
        adsRuleCount: engineRuleCount(this.adsEngine),
        trackerRuleCount: engineRuleCount(this.trackersEngine),
        updateInProgress: this.refreshRunning,
        lastUpdateFailed: this.refreshHadFailure,
      },
    };
  }

  logBlocked(category, details, host, rule, outcome) {
    this.blockedLogCount += 1;
    if (!this.debug && this.blockedLogCount > 25) return;
    const suffix = rule ? ` rule=${rule}` : '';
    this.logger.info(`[AdBlock] ${outcome} ${category} request type=${details.resourceType || 'other'} host=${host}${suffix}`);
  }

  logDecision(details, decision) {
    if (!this.debug || this.diagnosticLogCount >= 300) return;
    const targetHost = getHost(details.url);
    const sourceHost = getHost(details.initiatorOrigin || details.referrer || '');
    if (!isYouTubeHost(targetHost) && !isGoogleVideoHost(targetHost) && !isYouTubeHost(sourceHost)) return;
    this.diagnosticLogCount += 1;
    const filter = decision.filter?.rawLine ? ` rule=${decision.filter.rawLine}` : '';
    this.logger.info(`[AdBlock] decision=${decision.action} category=${decision.category || 'none'} type=${details.resourceType || 'other'} host=${targetHost || 'unknown'} source=${sourceHost || 'unknown'} reason=${decision.reason || 'matched-filter'}${filter}`);
  }

  runCosmeticOperation(sender, method, args) {
    try {
      const result = sender[method](...args);
      if (result && typeof result.then === 'function') {
        return result.catch(error => {
          this.logger.warn(`[Protection] Cosmetic ${method} failed (${error?.name || 'error'}).`);
          return undefined;
        });
      }
      return result;
    } catch (error) {
      this.logger.warn(`[Protection] Cosmetic ${method} failed (${error?.name || 'error'}).`);
      return undefined;
    }
  }

  snapshotPath(kind) { return path.join(this.cacheDirectory, FILTER_FILES[kind]); }

  async loadSnapshots() {
    let state = {};
    try { state = JSON.parse(await fs.readFile(path.join(this.cacheDirectory, FILTER_STATE_FILE), 'utf8')); }
    catch { /* A missing or malformed state file selects the fallback path. */ }
    this.filterState = state?.packageVersion === FILTER_PACKAGE_VERSION ? state : { packageVersion: FILTER_PACKAGE_VERSION };
    this.snapshotLoaded = { ads: false, trackers: false };

    for (const kind of ['ads', 'trackers']) {
      try {
        const file = this.snapshotPath(kind);
        const stat = await fs.stat(file);
        if (stat.size <= 0 || stat.size > MAX_SNAPSHOT_BYTES) throw new Error('snapshot size is invalid');
        const engine = ElectronBlocker.deserialize(await fs.readFile(file));
        if (engineRuleCount(engine) < 100) throw new Error('snapshot has too few network rules');
        if (kind === 'ads') this.adsEngine = engine;
        else this.trackersEngine = engine;
        this.snapshotLoaded[kind] = true;
      } catch (error) {
        this.logger.info(`[Protection] ${kind} filter snapshot unavailable; using built-in safety rules (${error.message}).`);
      }
    }
  }

  isStale(kind) {
    const updatedAt = Number(this.filterState?.[kind]?.updatedAt || 0);
    return !Number.isFinite(updatedAt) || Date.now() - updatedAt >= FILTER_TTL_MS;
  }

  scheduleFilterRefresh(delay = FILTER_TTL_MS) {
    clearTimeout(this.refreshTimer);
    this.refreshTimer = setTimeout(() => this.refreshFilterLists(), delay);
    this.refreshTimer.unref?.();
  }

  async refreshFilterLists() {
    if (this.refreshRunning) return;
    this.refreshRunning = true;
    this.refreshHadFailure = false;
    const tasks = [];
    if (!this.snapshotLoaded?.ads || this.isStale('ads')) tasks.push(this.refreshOneList('ads'));
    if (!this.snapshotLoaded?.trackers || this.isStale('trackers')) tasks.push(this.refreshOneList('trackers'));
    try {
      await Promise.all(tasks);
    } finally {
      this.refreshRunning = false;
      filterFetchCache.clear();
      const retrySoon = tasks.length > 0 && this.refreshHadFailure;
      this.scheduleFilterRefresh(retrySoon ? RETRY_MS : FILTER_TTL_MS);
    }
  }

  async refreshOneList(kind) {
    try {
      const engine = await this.loadFilterList(kind);
      const count = engineRuleCount(engine);
      if (count < 1000) throw new Error(`list compilation returned only ${count} network rules`);

      try { await atomicWrite(this.snapshotPath(kind), engine.serialize()); }
      catch (error) { this.logger.warn(`[Protection] Could not cache ${kind} filters: ${error.message}`); }

      if (kind === 'ads') this.replaceAdsEngine(engine);
      else this.trackersEngine = engine;
      this.snapshotLoaded[kind] = true;
      this.filterState[kind] = {
        updatedAt: Date.now(),
        ruleCount: count,
        sourceListCount: kind === 'ads' ? FILTER_AD_LISTS.length : TRACKER_LISTS.length,
      };
      this.filterState.packageVersion = FILTER_PACKAGE_VERSION;
      await atomicWrite(path.join(this.cacheDirectory, FILTER_STATE_FILE), JSON.stringify(this.filterState));
      this.logger.info(`[Protection] Updated ${kind} filter lists (${count} network rules).`);
    } catch (error) {
      // Keep the last verified snapshot/fallback in service on update failures.
      this.logger.warn(`[Protection] ${kind} filter update failed; existing rules remain active: ${error.message}`);
      this.refreshHadFailure = true;
    }
  }

  loadFilterList(kind) {
    return kind === 'ads'
      ? ElectronBlocker.fromLists(sharedFilterFetch, FILTER_AD_LISTS, ENGINE_CONFIG)
      : ElectronBlocker.fromLists(sharedFilterFetch, TRACKER_LISTS, TRACKER_ENGINE_CONFIG);
  }

  replaceAdsEngine(engine) {
    if (this.adSessionEnabled) {
      try { this.adsEngine.disableBlockingInSession(this.session); }
      catch (error) { this.logger.warn(`[Protection] Could not detach previous cosmetic engine: ${error.message}`); }
    }
    this.adsEngine = engine;
    try {
      this.adsEngine.enableBlockingInSession(this.session);
      this.adSessionEnabled = true;
    } catch (error) {
      this.adSessionEnabled = false;
      this.logger.warn(`[Protection] Updated network rules loaded; cosmetic hook unavailable: ${error.message}`);
    }
    this.bindManagedHandlers();
  }

  stop() {
    clearTimeout(this.refreshTimer);
    if (this.adSessionEnabled) {
      try { this.adsEngine.disableBlockingInSession(this.session); }
      catch { /* Electron is shutting down. */ }
      this.adSessionEnabled = false;
    }
    this.requestHookInstalled = false;
  }
}

module.exports = {
  ChromiumProtection,
  FILTER_TTL_MS,
  TRACKER_LISTS,
  compileCustomRules,
  decideRequest,
  isProtectedPlaybackRequest,
  makeFilterRequest,
  normalizeCustomRule,
};
