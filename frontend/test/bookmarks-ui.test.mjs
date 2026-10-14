import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('bookmark bar provides persisted ordering, folders, edit, search, and in-app opening', async () => {
  const component = await readFile(new URL('../src/components/BookmarksBar.jsx', import.meta.url), 'utf8');
  const styles = await readFile(new URL('../src/components/BookmarksBar.css', import.meta.url), 'utf8');
  assert.match(component, /cachebrowse\.bookmark-order/);
  assert.match(component, /cachebrowse\.bookmark-folders/);
  assert.match(component, /Search bookmarks/);
  assert.match(component, /Edit bookmark/);
  assert.match(component, /Open in new tab/);
  assert.match(component, /onDragStart/);
  assert.match(component, /onOpenNewTab\?\.\(item\.url\)/);
  assert.doesNotMatch(component, /window\.open\(item\.url/);
  assert.match(styles, /overflow-x:\s*auto/);
  assert.match(styles, /bookmarks-bar__all/);
});

test('profile and protection menus render through a viewport-level popup portal', async () => {
  const [portal, profile, protection] = await Promise.all([
    readFile(new URL('../src/components/PopupLayer.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/UserMenu.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/ProtectionMenu.jsx', import.meta.url), 'utf8'),
  ]);
  assert.match(portal, /createPortal/);
  assert.match(portal, /addEventListener\('resize'/);
  assert.match(portal, /addEventListener\('scroll'/);
  assert.match(portal, /event\.key === 'Escape'/);
  assert.match(profile, /<PopupLayer/);
  assert.match(profile, /Dashboard/);
  assert.match(profile, /Sign Out/);
  assert.match(protection, /<PopupLayer/);
  assert.match(protection, /Ad Blocking/);
  assert.match(protection, /Tracker Blocking/);
});

test('profile and protection triggers share one popup state and profile actions close through the owner', async () => {
  const [toolbar, profile] = await Promise.all([
    readFile(new URL('../src/components/BrowserToolbar.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/UserMenu.jsx', import.meta.url), 'utf8'),
  ]);
  assert.match(toolbar, /const \[activePopup, setActivePopup\] = useState\(null\)/);
  assert.match(toolbar, /setActivePopup\(current => current === kind \? null : kind\)/);
  assert.match(toolbar, /open=\{activePopup === 'protection'\}/);
  assert.match(toolbar, /open=\{activePopup === 'profile'\}/);
  assert.doesNotMatch(profile, /setOpen\(/);
  assert.match(profile, /close\(\); onDashboard/);
  assert.match(profile, /close\(\); onSettings/);
  assert.match(profile, /close\(\); onLogout/);
});

test('public browser client has no administrator portal or user-directory client', async () => {
  const [app, toolbar, profile, api] = await Promise.all([
    readFile(new URL('../src/App.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/BrowserToolbar.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/UserMenu.jsx', import.meta.url), 'utf8'),
    readFile(new URL('../src/services/api.js', import.meta.url), 'utf8'),
  ]);
  assert.doesNotMatch(app, /AdminDashboardPage|panel === ['"]admin['"]|onAdmin/);
  assert.doesNotMatch(toolbar, /onAdmin/);
  assert.doesNotMatch(profile, /admin|onAdmin/i);
  assert.doesNotMatch(api, /adminApi|\/admin\//);
  assert.match(api, /authApi/);
});
