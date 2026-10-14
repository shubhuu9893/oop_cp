const path = require('node:path');
const fs = require('node:fs');
const { spawn } = require('node:child_process');
const { randomUUID } = require('node:crypto');
const { ChromiumProtection } = require('./protection.cjs');
const {
  app, BrowserWindow, WebContentsView, session, ipcMain, Menu,
} = require('electron');

const debuggingPort = Number(process.env.CACHEBROWSE_REMOTE_DEBUGGING_PORT || 0);
if (Number.isInteger(debuggingPort) && debuggingPort >= 1024 && debuggingPort <= 65535) {
  app.commandLine.appendSwitch('remote-debugging-address', '127.0.0.1');
  app.commandLine.appendSwitch('remote-debugging-port', String(debuggingPort));
}

function configuredPort(value, fallback) {
  const port = Number(value);
  return Number.isInteger(port) && port >= 1024 && port <= 65535 ? port : fallback;
}

const API_PORT = configuredPort(process.env.CACHEBROWSE_API_PORT, 8080);
const API = `http://127.0.0.1:${API_PORT}/api`;
const FRONTEND_DIR = app.getAppPath();
const PROJECT_ROOT = path.resolve(FRONTEND_DIR, '..');

let windowRef;
let browserSession;
let backendProcess;
let ownsBackend = false;
let protectionController;
let overlayView;
let overlayReady = false;
let overlayAttached = false;
let activePopup = null;
let protectionRefreshPromise;
let backendStatus = { online: false, starting: true };
let activeTabId;
let pageBounds = { x: 0, y: 0, width: 0, height: 0 };
let browserPanelVisible = true;
// Keep built-in protection active while the optional Java API starts. Once the
// backend is ready its persisted settings replace these safe defaults.
let protection = { adBlock: true, trackerBlock: true, adRules: [], trackerRules: [], allowlist: [] };
const tabs = new Map();
const downloads = new Map();
const activeDownloads = new Map();

async function loadDownloads() {
  const file = path.join(app.getPath('userData'), 'downloads.json');
  try {
    const saved = JSON.parse(await fs.promises.readFile(file, 'utf8'));
    const directory = path.resolve(app.getPath('downloads'));
    if (!Array.isArray(saved)) return;
    for (const entry of saved) {
      if (!entry || typeof entry.id !== 'string' || typeof entry.path !== 'string') continue;
      const resolved = path.resolve(entry.path);
      const relative = path.relative(directory, resolved);
      if (relative.startsWith('..') || path.isAbsolute(relative)) continue;
      downloads.set(entry.id, entry);
    }
  } catch { /* First launch has no download history. */ }
}

async function saveDownloads() {
  const directory = app.getPath('userData');
  const file = path.join(directory, 'downloads.json');
  const temporary = `${file}.tmp`;
  await fs.promises.mkdir(directory, { recursive: true });
  await fs.promises.writeFile(temporary, JSON.stringify([...downloads.values()]), { mode: 0o600 });
  await fs.promises.rename(temporary, file);
}

async function apiRequest(pathname, options = {}) {
  const response = await fetch(`${API}${pathname}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
    signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) throw new Error(`Java API returned HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}

async function waitForBackend() {
  try {
    await apiRequest('/health');
    return;
  } catch { /* Start our backend below. */ }

  const maven = process.env.MAVEN_CMD || (process.platform === 'win32' ? 'mvn.cmd' : 'mvn');
  backendProcess = spawn(maven, ['-q', 'compile', 'exec:java'], {
    cwd: PROJECT_ROOT,
    env: {
      ...process.env,
      CACHEBROWSE_API_PORT: String(API_PORT),
      CACHEBROWSE_FRONTEND_PORT: String(configuredPort(process.env.VITE_PORT || process.env.CACHEBROWSE_FRONTEND_PORT, 5173)),
    },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });
  ownsBackend = true;
  backendProcess.stdout.on('data', chunk => console.log(`[Java] ${chunk.toString().trimEnd()}`));
  backendProcess.stderr.on('data', chunk => console.error(`[Java] ${chunk.toString().trimEnd()}`));
  backendProcess.on('error', error => console.error(`[Java] Could not start Maven: ${error.message}`));

  for (let attempt = 0; attempt < 90; attempt++) {
    if (backendProcess.exitCode !== null) {
      throw new Error(`Java backend exited with status ${backendProcess.exitCode}`);
    }
    try {
      await apiRequest('/health');
      return;
    } catch {
      await new Promise(resolve => setTimeout(resolve, 500));
    }
  }
  throw new Error(`Java backend did not become ready at ${API}`);
}

function refreshProtection() {
  if (protectionRefreshPromise) return protectionRefreshPromise;
  const pending = Promise.all([
    apiRequest('/protection/status'),
    apiRequest('/protection/rules'),
  ]).then(([status, rules]) => {
    protection = {
      adBlock: status.adBlock === true,
      trackerBlock: status.trackerBlock === true,
      adRules: Array.isArray(rules.adRules) ? rules.adRules : [],
      trackerRules: Array.isArray(rules.trackerRules) ? rules.trackerRules : [],
      allowlist: Array.isArray(rules.allowlist) ? rules.allowlist : [],
    };
    protectionController?.setProtection(protection);
    return protectionController?.getRuntimeStatus() || { available: false };
  }).finally(() => {
    if (protectionRefreshPromise === pending) protectionRefreshPromise = null;
  });
  protectionRefreshPromise = pending;
  return pending;
}

function publicTab(tab) {
  const contents = tab.view.webContents;
  return {
    id: tab.id,
    title: contents.getTitle() || tab.title || 'New Tab',
    url: contents.getURL() === 'about:blank' ? '' : contents.getURL(),
    loading: contents.isLoading(),
    canGoBack: contents.navigationHistory.canGoBack(),
    canGoForward: contents.navigationHistory.canGoForward(),
    favicon: tab.favicon || '',
    error: tab.error || null,
  };
}

function isTrustedUiUrl(target) {
  const devUrl = process.env.VITE_DEV_SERVER_URL;
  try {
    if (devUrl && new URL(target).origin === new URL(devUrl).origin) return true;
  } catch { /* Reject malformed targets. */ }
  try {
    const parsed = new URL(target);
    return parsed.protocol === 'file:' && parsed.pathname === new URL(pathToFileUrl(path.join(FRONTEND_DIR, 'dist', 'index.html'))).pathname;
  } catch { return false; }
}

function sendToUi(channel, payload) {
  if (windowRef && !windowRef.isDestroyed() && !windowRef.webContents.isDestroyed()) {
    windowRef.webContents.send(channel, payload);
  }
}

function publishTabs() {
  sendToUi('browser:tabs', { tabs: [...tabs.values()].map(publicTab), activeTabId });
}

function updateBrowserViewVisibility() {
  let activeBounds = null;
  for (const tab of tabs.values()) {
    const active = tab.id === activeTabId;
    tab.view.setVisible(active && browserPanelVisible);
    if (active && browserPanelVisible) {
      tab.view.setBounds({ ...pageBounds });
      activeBounds = { ...pageBounds };
    }
  }
  return activeBounds;
}

function closePopupOverlay(id = null, reason = 'programmatic') {
  if (!activePopup || (id && activePopup.id !== id)) return false;
  const closedId = activePopup.id;
  activePopup = null;
  overlayView?.webContents.send('popup:state', null);
  overlayView?.setVisible(false);
  if (overlayView && overlayAttached && windowRef && !windowRef.isDestroyed()) {
    windowRef.contentView.removeChildView(overlayView);
    overlayAttached = false;
  }
  sendToUi('browser:popup-closed', { id: closedId, reason });
  return true;
}

function popupRendererState(popup) {
  return { ...popup, bounds: { x: 0, y: 0, width: popup.bounds.width, height: popup.bounds.height } };
}

function updatePopupOverlay(message) {
  if (!message || !['profile', 'protection', 'bookmarks', 'bookmark-context'].includes(message.kind)
      || typeof message.id !== 'string' || message.id.length > 100) {
    throw new Error('Invalid popup overlay request');
  }
  const bounds = message.bounds || {};
  const values = ['x', 'y', 'width', 'height'].map(key => Number(bounds[key]));
  if (!values.every(Number.isFinite) || values[0] < 0 || values[1] < 0 || values[2] < 1 || values[3] < 1) {
    throw new Error('Invalid popup bounds');
  }
  const payload = message.payload && typeof message.payload === 'object' ? message.payload : {};
  if (Buffer.byteLength(JSON.stringify(payload), 'utf8') > 262_144) throw new Error('Popup data is too large');
  if (!overlayView) throw new Error('Popup overlay renderer is not ready');
  const [width, height] = windowRef.getContentSize();
  const x = Math.max(4, Math.min(values[0], width - Math.min(values[2], width) - 4));
  const y = Math.max(4, Math.min(values[1], height - Math.min(values[3], height) - 4));
  const raiseOverlay = activePopup?.id !== message.id || !overlayView.getVisible();
  activePopup = { id: message.id, kind: message.kind, payload, bounds: { x, y, width: Math.min(values[2], width - 8), height: Math.min(values[3], height - 8) } };
  if (!overlayAttached) {
    windowRef.contentView.addChildView(overlayView);
    overlayAttached = true;
  } else if (raiseOverlay) {
    windowRef.contentView.removeChildView(overlayView);
    windowRef.contentView.addChildView(overlayView);
  }
  // A WebContentsView receives native pointer input across its entire bounds,
  // including transparent pixels. Keep the native surface limited to the
  // actual popup instead of covering the toolbar, tabs, and webpage.
  overlayView.setBounds(activePopup.bounds);
  overlayView.setBackgroundColor('#00000000');
  overlayView.webContents.send('popup:state', popupRendererState(activePopup));
  overlayView.setVisible(overlayReady);
  return true;
}

function emitTab(tab, event = 'state') {
  if (!tabs.has(tab.id)) return;
  sendToUi('browser:tab-event', { event, tab: publicTab(tab) });
  publishTabs();
}

function navigateTab(tab, url) {
  let parsed;
  try { parsed = new URL(url); } catch { throw new Error('Enter a valid URL or search query.'); }
  if (!['http:', 'https:'].includes(parsed.protocol) || !parsed.hostname || parsed.username || parsed.password) {
    throw new Error('Only HTTP and HTTPS websites can be opened.');
  }
  tab.error = null;
  return tab.view.webContents.loadURL(parsed.toString());
}

function watchNavigation(tab, navigation) {
  navigation.catch(error => {
    if (error?.errno === -3 || error?.code === 'ERR_ABORTED') return;
    if (!tabs.has(tab.id) || tab.view.webContents.isDestroyed() || tab.error) return;
    tab.error = 'Unable to load this page. Check the connection and try again.';
    emitTab(tab, 'error');
  });
}

function createTab(url = '') {
  if (tabs.size >= 50) throw new Error('Close a tab before opening another one.');
  closePopupOverlay();
  const id = randomUUID();
  const view = new WebContentsView({
    webPreferences: {
      session: browserSession,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
    },
  });
  const tab = { id, view, title: 'New Tab', favicon: '', error: null, lastHistoryUrl: '' };
  tabs.set(id, tab);
  windowRef.contentView.addChildView(view);
  view.setBounds({ ...pageBounds });
  view.setVisible(false);

  const contents = view.webContents;
  contents.setWindowOpenHandler(({ url: target }) => {
    createTab(target);
    return { action: 'deny' };
  });
  contents.on('will-navigate', (event, target) => {
    try {
      if (!['http:', 'https:'].includes(new URL(target).protocol)) event.preventDefault();
    } catch { event.preventDefault(); }
  });
  contents.on('will-redirect', (event, target) => {
    try {
      if (!['http:', 'https:'].includes(new URL(target).protocol)) event.preventDefault();
    } catch { event.preventDefault(); }
  });
  contents.on('did-start-loading', () => { tab.error = null; emitTab(tab, 'loading'); });
  contents.on('did-stop-loading', () => emitTab(tab, 'loaded'));
  contents.on('did-navigate', (_event, target) => {
    tab.error = null;
    emitTab(tab, 'navigate');
  });
  contents.on('did-navigate-in-page', () => emitTab(tab, 'navigate'));
  contents.on('page-title-updated', (_event, title) => { tab.title = title; emitTab(tab, 'title'); });
  contents.on('page-favicon-updated', (_event, favicons) => {
    tab.favicon = Array.isArray(favicons) ? favicons[0] || '' : '';
    emitTab(tab, 'favicon');
  });
  contents.on('did-fail-load', (_event, errorCode, description, validatedURL, isMainFrame) => {
    if (isMainFrame && errorCode !== -3) {
      tab.error = `Unable to load ${validatedURL}: ${description || 'network error'}`;
      emitTab(tab, 'error');
    }
  });
  contents.on('render-process-gone', (_event, details) => {
    tab.error = `Page process stopped (${details.reason}). Reload to try again.`;
    emitTab(tab, 'error');
  });
  contents.on('console-message', details => {
    const host = (() => { try { return new URL(contents.getURL()).hostname; } catch { return ''; } })();
    if (details.level === 'error' || /youtube|media|codec|mse|gpu/i.test(details.message)) {
      console[details.level === 'error' ? 'error' : 'info'](
        `[Page ${host || tab.id}] ${details.message}${details.sourceId ? ` (${details.sourceId}:${details.lineNumber})` : ''}`,
      );
    }
  });
  contents.on('media-started-playing', () => console.info(`[Media ${tab.id}] started ${contents.getURL()}`));
  contents.on('media-paused', () => console.info(`[Media ${tab.id}] paused ${contents.getURL()}`));
  contents.on('media-stopped-playing', () => console.info(`[Media ${tab.id}] stopped ${contents.getURL()}`));

  activeTabId = id;
  updateBrowserViewVisibility();
  publishTabs();
  if (url) {
    try { watchNavigation(tab, navigateTab(tab, url)); }
    catch { tab.error = 'Unable to open this address.'; emitTab(tab, 'error'); }
  }
  return tab;
}

function activeTab() { return tabs.get(activeTabId) || null; }

function closeTab(id) {
  const tab = tabs.get(id);
  if (!tab) return;
  windowRef.contentView.removeChildView(tab.view);
  if (!tab.view.webContents.isDestroyed()) tab.view.webContents.destroy();
  tabs.delete(id);
  closePopupOverlay();
  if (activeTabId === id) activeTabId = [...tabs.keys()].at(-1) || null;
  if (!tabs.size) createTab();
  updateBrowserViewVisibility();
  publishTabs();
}

function validateUiSender(event) {
  const senderFrame = event.senderFrame;
  return windowRef && !windowRef.isDestroyed() && event.sender === windowRef.webContents
    && senderFrame?.parent === null && isTrustedUiUrl(senderFrame.url);
}

function registerIpc() {
  const handle = (channel, fn) => ipcMain.handle(channel, (event, ...args) => {
    if (!validateUiSender(event)) throw new Error('Untrusted IPC sender');
    return fn(...args);
  });
  handle('browser:get-tabs', () => ({ tabs: [...tabs.values()].map(publicTab), activeTabId }));
  handle('backend:get-status', () => ({ ...backendStatus }));
  handle('browser:get-protection-runtime-status', () => protectionController?.getRuntimeStatus() || { available: false });
  handle('browser:get-layout-metrics', () => ({
    window: windowRef?.getContentSize() || null,
    page: { ...pageBounds },
    popup: activePopup ? { ...activePopup.bounds } : null,
    popupKind: activePopup?.kind || null,
    // Report actual native bounds separately from the requested popup/page
    // rectangles so diagnostics can catch a stale WebContentsView covering UI.
    overlayBounds: overlayView?.getBounds() || null,
    overlayAttached,
    overlayVisible: Boolean(overlayView?.getVisible()),
    activePageVisible: Boolean(activeTab()?.view.getVisible()),
    activePageBounds: activeTab()?.view.getBounds() || null,
  }));
  handle('browser:show-popup-overlay', (message) => updatePopupOverlay(message));
  handle('browser:close-popup-overlay', id => closePopupOverlay(typeof id === 'string' ? id : null));
  handle('browser:new-tab', url => {
    const tab = createTab(typeof url === 'string' ? url : '');
    return tab.id;
  });
  handle('browser:close-tab', id => closeTab(String(id)));
  handle('browser:activate-tab', id => {
    if (!tabs.has(String(id))) throw new Error('Tab no longer exists');
    closePopupOverlay(); activeTabId = String(id); updateBrowserViewVisibility(); publishTabs();
  });
  handle('browser:duplicate-tab', id => {
    const tab = tabs.get(String(id));
    if (!tab) throw new Error('Tab no longer exists');
    return createTab(publicTab(tab).url).id;
  });
  handle('browser:navigate', (id, url) => {
    const tab = tabs.get(String(id));
    if (!tab) throw new Error('Tab no longer exists');
    watchNavigation(tab, navigateTab(tab, String(url)));
    return { started: true };
  });
  handle('browser:back', id => { const tab = tabs.get(String(id)); if (tab?.view.webContents.navigationHistory.canGoBack()) tab.view.webContents.navigationHistory.goBack(); });
  handle('browser:forward', id => { const tab = tabs.get(String(id)); if (tab?.view.webContents.navigationHistory.canGoForward()) tab.view.webContents.navigationHistory.goForward(); });
  handle('browser:reload', id => { const tab = tabs.get(String(id)); if (tab) tab.view.webContents.reload(); });
  handle('browser:stop', id => { const tab = tabs.get(String(id)); if (tab) tab.view.webContents.stop(); });
  handle('browser:home', id => {
    const tab = tabs.get(String(id));
    if (tab) tab.view.webContents.loadURL('about:blank').catch(() => {});
  });
  handle('browser:set-bounds', bounds => {
    const values = ['x', 'y', 'width', 'height'].map(key => Number(bounds?.[key]));
    if (!values.every(Number.isFinite) || values[2] < 0 || values[3] < 0) return;
    pageBounds = { x: Math.max(0, values[0]), y: Math.max(0, values[1]), width: values[2], height: values[3] };
    updateBrowserViewVisibility();
  });
  handle('browser:set-visible', visible => {
    if (visible !== true) closePopupOverlay(null, 'browser-hidden');
    browserPanelVisible = visible === true;
    updateBrowserViewVisibility();
  });
  handle('browser:clear-cache', async () => browserSession.clearCache());
  handle('browser:get-cache-size', async () => browserSession.getCacheSize());
  handle('browser:refresh-protection', refreshProtection);
  handle('api:request', async request => {
    if (!request || typeof request.path !== 'string' || !request.path.startsWith('/api/')) {
      throw new Error('Invalid API path');
    }
    const method = String(request.method || 'GET').toUpperCase();
    if (!['GET', 'POST', 'PUT', 'DELETE'].includes(method)) throw new Error('Unsupported API method');
    const target = new URL(request.path, `http://127.0.0.1:${API_PORT}`);
    if (target.origin !== `http://127.0.0.1:${API_PORT}` || !target.pathname.startsWith('/api/')) {
      throw new Error('API requests must target the local Java service');
    }
    const body = request.body == null ? undefined : String(request.body);
    if (body && Buffer.byteLength(body, 'utf8') > 65_536) throw new Error('API request body is too large');
    const authorization = typeof request.authorization === 'string'
      && request.authorization.length <= 8192
      && /^Bearer [A-Za-z0-9._~-]+$/.test(request.authorization)
      ? request.authorization : undefined;
    let response;
    try {
      response = await fetch(target, {
        method,
        headers: { 'Content-Type': 'application/json', ...(authorization ? { Authorization: authorization } : {}) },
        body,
        redirect: 'error',
        signal: AbortSignal.timeout(5000),
      });
    } catch (error) {
      const reason = error?.cause?.code || error?.name || 'network error';
      console.warn(`[API] ${method} ${target.pathname} unavailable (${reason}); keeping the browser UI active.`);
      return {
        status: 503,
        ok: false,
        contentType: 'application/json',
        body: { error: 'Java backend is unavailable.' },
      };
    }
    const contentType = response.headers.get('content-type') || '';
    const text = await response.text();
    let responseBody = text;
    if (contentType.includes('application/json') && text) {
      try { responseBody = JSON.parse(text); } catch { /* Preserve malformed response text for diagnostics. */ }
    }
    if (response.ok && (
      (['POST', 'DELETE'].includes(method) && /\/api\/protection\/(?:allowlist|blocklist)$/.test(target.pathname))
      || (method === 'PUT' && /\/api\/protection\/(?:adblock|tracker)$/.test(target.pathname))
    )) {
      setImmediate(() => refreshProtection().catch(error => {
        console.warn(`[Protection] Could not apply updated rules: ${error.message}`);
      }));
    }
    return { status: response.status, ok: response.ok, contentType, body: responseBody };
  });
  handle('downloads:list', () => [...downloads.values()]);
  handle('downloads:open', id => {
    const entry = downloads.get(String(id));
    if (!entry?.path) return false;
    const relative = path.relative(path.resolve(app.getPath('downloads')), path.resolve(entry.path));
    if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Download path is outside the downloads folder');
    const { shell } = require('electron');
    return shell.openPath(entry.path).then(result => !result);
  });
  handle('downloads:cancel', id => {
    const item = activeDownloads.get(String(id));
    if (!item) return false;
    item.cancel();
    return true;
  });
  ipcMain.on('popup:action', (event, message) => {
    if (!overlayView || event.sender !== overlayView.webContents || !activePopup
        || !message || message.id !== activePopup.id
        || !['dashboard', 'settings', 'signout', 'toggle-ad', 'toggle-tracker', 'query', 'folder', 'open', 'open-new-tab', 'edit', 'remove', 'create-folder', 'manage', 'edit-change', 'save', 'cancel-edit'].includes(message.action)) return;
    let value = message.value;
    try {
      if (Buffer.byteLength(JSON.stringify(value ?? null), 'utf8') > 16_384) return;
    } catch { return; }
    sendToUi('browser:popup-action', { id: activePopup.id, action: message.action, value });
  });
  ipcMain.on('popup:close', (event, reason) => {
    if (overlayView && event.sender === overlayView.webContents) closePopupOverlay(null, ['escape', 'outside'].includes(reason) ? reason : 'programmatic');
  });
}

function createAppMenu() {
  const menu = Menu.buildFromTemplate([
    { label: 'File', submenu: [
      { label: 'New Tab', accelerator: 'CmdOrCtrl+T', click: () => createTab() },
      { label: 'Close Tab', accelerator: 'CmdOrCtrl+W', click: () => activeTab() && closeTab(activeTabId) },
      { type: 'separator' }, { role: 'quit' },
    ] },
    { label: 'Edit', submenu: [{ role: 'undo' }, { role: 'redo' }, { type: 'separator' }, { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'selectAll' }] },
    { label: 'View', submenu: [
      { label: 'Focus Address Bar', accelerator: 'CmdOrCtrl+L', click: () => sendToUi('browser:focus-address') },
      { label: 'Back', accelerator: 'Alt+Left', click: () => activeTab()?.view.webContents.navigationHistory.canGoBack() && activeTab().view.webContents.navigationHistory.goBack() },
      { label: 'Forward', accelerator: 'Alt+Right', click: () => activeTab()?.view.webContents.navigationHistory.canGoForward() && activeTab().view.webContents.navigationHistory.goForward() },
      { label: 'Reload', accelerator: 'CmdOrCtrl+R', click: () => activeTab()?.view.webContents.reload() },
      { label: 'Stop Loading', accelerator: 'Esc', click: () => activeTab()?.view.webContents.stop() },
      { label: 'Developer Tools', accelerator: 'Alt+CmdOrCtrl+I', click: () => activeTab()?.view.webContents.toggleDevTools() },
    ] },
  ]);
  Menu.setApplicationMenu(menu);
}

function createWindow() {
  windowRef = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 900,
    minHeight: 640,
    backgroundColor: '#101318',
    title: 'CacheBrowse',
    webPreferences: {
      preload: path.join(FRONTEND_DIR, 'desktop', 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
  });
  windowRef.webContents.on('did-finish-load', () => {
    console.info(`[UI] Renderer document loaded: ${windowRef.webContents.getURL()}`);
  });
  windowRef.webContents.on('console-message', details => {
    const location = details.sourceId ? ` (${details.sourceId}:${details.lineNumber})` : '';
    const message = `[UI Console] ${details.message}${location}`;
    if (details.level === 'error') console.error(message);
    else if (details.level === 'warning') console.warn(message);
  });
  windowRef.webContents.on('did-fail-load', (_event, errorCode, description, url, isMainFrame) => {
    if (isMainFrame && errorCode !== -3) {
      console.error(`[UI] Renderer navigation failed (${errorCode}): ${description} — ${url}`);
    }
  });
  windowRef.webContents.on('render-process-gone', (_event, details) => {
    console.error(`[UI] Renderer process stopped: ${details.reason}`);
  });
  windowRef.webContents.on('will-navigate', (event, target) => {
    if (!isTrustedUiUrl(target)) event.preventDefault();
  });
  windowRef.webContents.on('will-redirect', (event, target) => {
    if (!isTrustedUiUrl(target)) event.preventDefault();
  });
  overlayView = new WebContentsView({
    webPreferences: {
      preload: path.join(FRONTEND_DIR, 'desktop', 'overlay-preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
  });
  overlayView.setBackgroundColor('#00000000');
  overlayView.setVisible(false);
  overlayView.webContents.on('will-navigate', (event, target) => {
    if (!isTrustedUiUrl(target)) event.preventDefault();
  });
  overlayView.webContents.on('will-redirect', (event, target) => {
    if (!isTrustedUiUrl(target)) event.preventDefault();
  });
  overlayView.webContents.on('did-finish-load', () => {
    overlayReady = true;
    if (activePopup) {
      overlayView.webContents.send('popup:state', popupRendererState(activePopup));
      overlayView.setVisible(true);
    }
  });
  windowRef.on('resize', () => {
    if (activePopup) {
      const [width, height] = windowRef.getContentSize();
      const popup = activePopup.bounds;
      popup.width = Math.max(1, Math.min(popup.width, width - 8));
      popup.height = Math.max(1, Math.min(popup.height, height - 8));
      popup.x = Math.max(4, Math.min(popup.x, width - popup.width - 4));
      popup.y = Math.max(4, Math.min(popup.y, height - popup.height - 4));
      overlayView?.setBounds(popup);
      overlayView?.webContents.send('popup:state', popupRendererState(activePopup));
    }
    sendToUi('browser:layout-needed');
  });
  windowRef.on('closed', () => { windowRef = null; });
  if (process.env.VITE_DEV_SERVER_URL) windowRef.loadURL(process.env.VITE_DEV_SERVER_URL);
  else windowRef.loadFile(path.join(FRONTEND_DIR, 'dist', 'index.html'));
  const overlayQuery = { cachebrowseOverlay: '1' };
  if (process.env.VITE_DEV_SERVER_URL) {
    const overlayUrl = new URL(process.env.VITE_DEV_SERVER_URL);
    overlayUrl.searchParams.set('cachebrowseOverlay', '1');
    overlayView.webContents.loadURL(overlayUrl.toString());
  } else {
    overlayView.webContents.loadFile(path.join(FRONTEND_DIR, 'dist', 'index.html'), { query: overlayQuery });
  }
}

function pathToFileUrl(filePath) {
  return require('node:url').pathToFileURL(filePath).href;
}

async function start() {
  try {
    browserSession = session.fromPartition('persist:cachebrowse');
    browserSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
    browserSession.setPermissionCheckHandler(() => false);
    app.on('gpu-info-update', () => {
      app.getGPUInfo('basic').then(info => console.info(`[GPU] status=${info?.gpuDevice?.[0]?.deviceString || 'unavailable'}`))
        .catch(error => console.warn(`[GPU] diagnostics unavailable: ${error.message}`));
    });
    await loadDownloads();
    protectionController = new ChromiumProtection({
      session: browserSession,
      ipcMain,
      cacheDirectory: path.join(app.getPath('userData'), 'filter-lists'),
      onBlocked: category => apiRequest('/protection/blocked', {
        method: 'POST', body: JSON.stringify({ category }),
      }),
    });
    await protectionController.start(protection);
    createAppMenu();
    registerIpc();
    createWindow();
    createTab();
    browserSession.on('will-download', (_event, item) => {
      const id = randomUUID();
      const suggestedName = item.getFilename().replace(/[\u0000-\u001f\u007f]/g, '_').slice(0, 240);
      const baseName = path.basename(suggestedName) || `download-${id}`;
      const extension = path.extname(baseName);
      const stem = path.basename(baseName, extension);
      const directory = app.getPath('downloads');
      fs.mkdirSync(directory, { recursive: true });
      let safeName = baseName;
      let suffix = 1;
      while (fs.existsSync(path.join(directory, safeName))) {
        safeName = `${stem} (${suffix++})${extension}`;
      }
      const savePath = path.join(directory, safeName);
      item.setSavePath(savePath);
      const record = { id, filename: safeName, path: savePath, receivedBytes: 0, totalBytes: item.getTotalBytes(), state: 'progressing', createdAt: new Date().toISOString() };
      downloads.set(id, record);
      activeDownloads.set(id, item);
      sendToUi('downloads:changed', { ...record });
      item.on('updated', (_e, state) => {
        record.receivedBytes = item.getReceivedBytes();
        record.totalBytes = item.getTotalBytes();
        if (state === 'interrupted') record.state = 'interrupted';
        sendToUi('downloads:changed', { ...record });
      });
      item.once('done', (_e, state) => {
        record.state = state;
        activeDownloads.delete(id);
        sendToUi('downloads:changed', { ...record });
        saveDownloads().catch(error => console.error(`[Downloads] Could not save metadata: ${error.message}`));
      });
    });

    waitForBackend().then(async () => {
      backendStatus = { online: true, starting: false };
      sendToUi('backend:status', backendStatus);
      try {
        await refreshProtection();
        console.info('[Protection] Browser request rules are ready.');
      } catch (error) {
        console.warn(`[Protection] Rules are temporarily unavailable: ${error.message}`);
      }
    }).catch(error => {
      backendStatus = { online: false, starting: false, message: 'Java services are unavailable.' };
      sendToUi('backend:status', backendStatus);
      console.error(`[Backend] Java API is unavailable; browser shell remains active: ${error.message}`);
    });
  } catch (error) {
    console.error(`[CacheBrowse] Startup failed: ${error.stack || error.message}`);
    if (!windowRef || windowRef.isDestroyed()) app.quit();
    else {
      backendStatus = { online: false, starting: false, message: 'Application startup failed.' };
      sendToUi('backend:status', backendStatus);
    }
  }
}

app.whenReady().then(start);
app.on('window-all-closed', () => app.quit());
app.on('before-quit', () => {
  protectionController?.stop();
  for (const tab of tabs.values()) {
    if (!tab.view.webContents.isDestroyed()) tab.view.webContents.destroy();
  }
  tabs.clear();
  if (ownsBackend && backendProcess && backendProcess.exitCode === null) backendProcess.kill();
});
