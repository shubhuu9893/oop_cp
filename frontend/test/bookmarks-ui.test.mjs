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
