import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { createServer } from 'vite';

test('React local web shell renders without Electron preload or backend services', async () => {
  const previousWindow = globalThis.window;
  const previousLocalStorage = globalThis.localStorage;
  globalThis.window = { cacheBrowse: undefined };
  globalThis.localStorage = { getItem: () => null, setItem: () => {} };
  const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' });

  try {
    const { default: App } = await server.ssrLoadModule('/src/App.jsx');
    const html = renderToString(React.createElement(App));
    assert.match(html, /class="cb-app"/);
    assert.match(html, /class="cb-content"/);
    assert.match(html, /Search the web or enter a URL/);
    assert.match(html, /Java services unavailable/);
  } finally {
    await server.close();
    if (previousWindow === undefined) delete globalThis.window;
    else globalThis.window = previousWindow;
    if (previousLocalStorage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = previousLocalStorage;
  }
});

test('normal development workflow launches the in-app Chromium browser', async () => {
  const pkg = JSON.parse(await readFile(path.resolve('package.json'), 'utf8'));
  const viewport = await readFile(path.resolve('src/components/BrowserViewport.jsx'), 'utf8');
  const config = await readFile(path.resolve('vite.config.js'), 'utf8');
  const launcher = await readFile(path.resolve('desktop/dev.mjs'), 'utf8');
  const main = await readFile(path.resolve('desktop/main.cjs'), 'utf8');
  assert.equal(pkg.scripts.dev, 'node desktop/dev.mjs');
  assert.equal(pkg.scripts['dev:web'], 'vite --host localhost --port 5173 --strictPort');
  assert.equal(pkg.scripts['dev:desktop'], 'node desktop/dev.mjs');
  assert.match(config, /host: 'localhost'/);
  assert.match(launcher, /http:\/\/localhost:\$\{port\}/);
  assert.match(launcher, /'--host', 'localhost'/);
  assert.doesNotMatch(launcher, /http:\/\/127\.0\.0\.1:\$\{port\}/);
  assert.match(viewport, /cannot render arbitrary websites/);
  assert.match(main, /new WebContentsView/);
  assert.match(main, /contextIsolation: true/);
  assert.match(main, /nodeIntegration: false/);
  assert.doesNotMatch(viewport, /target="_blank"/);
  assert.doesNotMatch(viewport, /<iframe/i);
});

test('source index shows a useful diagnostic if opened directly as a file', async () => {
  const html = await readFile(path.resolve('index.html'), 'utf8');
  assert.match(html, /CacheBrowse UI has not started/);
  assert.match(html, /Do not open/);
  assert.match(html, /npm run dev/);
});
