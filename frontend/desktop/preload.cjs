const { contextBridge, ipcRenderer } = require('electron');

const subscriptions = {
  tabs: 'browser:tabs',
  tabEvent: 'browser:tab-event',
  focusAddress: 'browser:focus-address',
  layoutNeeded: 'browser:layout-needed',
  downloadChanged: 'downloads:changed',
  backendStatus: 'backend:status',
  popupClosed: 'browser:popup-closed',
  popupAction: 'browser:popup-action',
};

function subscribe(channel, callback) {
  const eventName = subscriptions[channel];
  if (!eventName || typeof callback !== 'function') return () => {};
  const listener = (_event, payload) => callback(payload);
  ipcRenderer.on(eventName, listener);
  return () => ipcRenderer.removeListener(eventName, listener);
}

contextBridge.exposeInMainWorld('cacheBrowse', Object.freeze({
  apiBaseUrl: '/api',
  getBackendStatus: () => ipcRenderer.invoke('backend:get-status'),
  getProtectionRuntimeStatus: () => ipcRenderer.invoke('browser:get-protection-runtime-status'),
  requestApi: (path, options = {}) => ipcRenderer.invoke('api:request', {
    path: String(path),
    method: String(options.method || 'GET'),
    body: typeof options.body === 'string' ? options.body : undefined,
    authorization: typeof options.headers?.Authorization === 'string' ? options.headers.Authorization : undefined,
  }),
  getTabs: () => ipcRenderer.invoke('browser:get-tabs'),
  newTab: url => ipcRenderer.invoke('browser:new-tab', typeof url === 'string' ? url : ''),
  closeTab: id => ipcRenderer.invoke('browser:close-tab', String(id)),
  activateTab: id => ipcRenderer.invoke('browser:activate-tab', String(id)),
  duplicateTab: id => ipcRenderer.invoke('browser:duplicate-tab', String(id)),
  navigate: (id, url) => ipcRenderer.invoke('browser:navigate', String(id), String(url)),
  back: id => ipcRenderer.invoke('browser:back', String(id)),
  forward: id => ipcRenderer.invoke('browser:forward', String(id)),
  reload: id => ipcRenderer.invoke('browser:reload', String(id)),
  stop: id => ipcRenderer.invoke('browser:stop', String(id)),
  home: id => ipcRenderer.invoke('browser:home', String(id)),
  setPageBounds: bounds => ipcRenderer.invoke('browser:set-bounds', bounds),
  showPopupOverlay: popup => ipcRenderer.invoke('browser:show-popup-overlay', popup),
  closePopupOverlay: id => ipcRenderer.invoke('browser:close-popup-overlay', String(id)),
  getLayoutMetrics: () => ipcRenderer.invoke('browser:get-layout-metrics'),
  setBrowserVisible: visible => ipcRenderer.invoke('browser:set-visible', visible === true),
  clearBrowserCache: () => ipcRenderer.invoke('browser:clear-cache'),
  getBrowserCacheSize: () => ipcRenderer.invoke('browser:get-cache-size'),
  refreshProtection: () => ipcRenderer.invoke('browser:refresh-protection'),
  getDownloads: () => ipcRenderer.invoke('downloads:list'),
  openDownload: id => ipcRenderer.invoke('downloads:open', String(id)),
  cancelDownload: id => ipcRenderer.invoke('downloads:cancel', String(id)),
  subscribe,
}));
