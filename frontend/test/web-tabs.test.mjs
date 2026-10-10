import assert from 'node:assert/strict';
import test from 'node:test';

const storage = new Map();
globalThis.localStorage = {
  getItem: key => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, value),
};
const { webTabs } = await import('../src/services/webTabs.js');

test('web mode stores destination history independently per app tab', () => {
  const first = webTabs.getState().activeTabId;
  webTabs.navigate(first, 'https://example.com/');
  const second = webTabs.newTab();
  webTabs.navigate(second, 'https://wikipedia.org/');
  webTabs.navigate(second, 'https://github.com/');
  webTabs.back(second);

  const state = webTabs.getState();
  const firstTab = state.tabs.find(tab => tab.id === first);
  const secondTab = state.tabs.find(tab => tab.id === second);
  assert.equal(firstTab.url, 'https://example.com/');
  assert.equal(secondTab.url, 'https://wikipedia.org/');
  assert.equal(secondTab.historyIndex, 0);
  assert.equal(state.activeTabId, second);
});

test('web-mode tabs persist and closing the active tab selects its neighbor', () => {
  const active = webTabs.getState().activeTabId;
  const saved = JSON.parse(storage.get('cachebrowse.web-tab-targets.v1'));
  assert.ok(saved.tabs.some(tab => tab.id === active));
  webTabs.closeTab(active);
  assert.notEqual(webTabs.getState().activeTabId, active);
});
