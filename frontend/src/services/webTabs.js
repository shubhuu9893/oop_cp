const STORAGE_KEY = 'cachebrowse.web-tab-targets.v1';
const listeners = new Set();

function readState() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
    if (Array.isArray(saved.tabs) && saved.tabs.length) {
      return { tabs: saved.tabs, activeTabId: saved.activeTabId || saved.tabs[0].id };
    }
  } catch { /* Start with a clean local-only tab model. */ }
  const first = { id: `web-${crypto.randomUUID()}`, title: 'New Tab', url: '', history: [], historyIndex: -1 };
  return { tabs: [first], activeTabId: first.id };
}

let state = readState();

function publish() {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch { /* Persistence is optional. */ }
  for (const listener of [...listeners]) listener(state);
}

function tabTitle(url) {
  if (!url) return 'New Tab';
  try { return new URL(url).hostname.replace(/^www\./, '') || url; } catch { return url; }
}

export const webTabs = {
  getState: () => state,
  subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
  newTab() {
    const tab = { id: `web-${crypto.randomUUID()}`, title: 'New Tab', url: '', history: [], historyIndex: -1 };
    state = { tabs: [...state.tabs, tab], activeTabId: tab.id };
    publish();
    return tab.id;
  },
  closeTab(id) {
    if (state.tabs.length <= 1) return this.resetTab(id);
    const index = state.tabs.findIndex(tab => tab.id === id);
    const tabs = state.tabs.filter(tab => tab.id !== id);
    const activeTabId = state.activeTabId === id ? tabs[Math.max(0, index - 1)].id : state.activeTabId;
    state = { tabs, activeTabId };
    publish();
  },
  resetTab(id) {
    state = { ...state, tabs: state.tabs.map(tab => tab.id === id
      ? { ...tab, title: 'New Tab', url: '', history: [], historyIndex: -1 } : tab) };
    publish();
  },
  activateTab(id) { if (state.tabs.some(tab => tab.id === id)) { state = { ...state, activeTabId: id }; publish(); } },
  duplicateTab(id) {
    const source = state.tabs.find(tab => tab.id === id);
    if (!source) return this.newTab();
    const copy = { ...source, id: `web-${crypto.randomUUID()}`, history: [...source.history] };
    state = { tabs: [...state.tabs, copy], activeTabId: copy.id };
    publish();
    return copy.id;
  },
  navigate(id, url) {
    state = { ...state, tabs: state.tabs.map(tab => {
      if (tab.id !== id) return tab;
      const history = [...tab.history.slice(0, tab.historyIndex + 1), url];
      return { ...tab, url, title: tabTitle(url), history, historyIndex: history.length - 1 };
    }) };
    publish();
  },
  back(id) { this.move(id, -1); },
  forward(id) { this.move(id, 1); },
  move(id, delta) {
    state = { ...state, tabs: state.tabs.map(tab => {
      if (tab.id !== id) return tab;
      const historyIndex = tab.historyIndex + delta;
      if (historyIndex < 0 || historyIndex >= tab.history.length) return tab;
      const url = tab.history[historyIndex];
      return { ...tab, url, title: tabTitle(url), historyIndex };
    }) };
    publish();
  },
};
