const test = require('node:test');
const assert = require('node:assert/strict');
const { ElectronBlocker } = require('@ghostery/adblocker-electron');
const {
  ChromiumProtection,
  compileCustomRules,
  decideRequest,
  isProtectedPlaybackRequest,
  normalizeCustomRule,
} = require('../desktop/protection.cjs');

const adsEngine = ElectronBlocker.parse('||doubleclick.net^$script,xmlhttprequest,third-party\n||googlevideo.com^', {
  loadCosmeticFilters: false,
  loadNetworkFilters: true,
});
const trackersEngine = ElectronBlocker.parse('||analytics.example^$xmlhttprequest,third-party', {
  loadCosmeticFilters: false,
  loadNetworkFilters: true,
});
const emptyEngine = ElectronBlocker.empty({ loadCosmeticFilters: false, loadNetworkFilters: true });

function decision(details, overrides = {}) {
  return decideRequest({
    details: { id: 1, timestamp: 1, resourceType: 'xhr', ...details },
    protection: { adBlock: true, trackerBlock: true, allowlist: [], ...overrides.protection },
    adsEngine: overrides.adsEngine || adsEngine,
    trackersEngine: overrides.trackersEngine || trackersEngine,
    fallbackAdsEngine: overrides.fallbackAdsEngine || emptyEngine,
    fallbackTrackersEngine: overrides.fallbackTrackersEngine || emptyEngine,
    customAds: overrides.customAds || [],
    customTrackers: overrides.customTrackers || [],
  });
}

test('EasyList-compatible network rules block a third-party YouTube ad request', () => {
  const result = decision({
    url: 'https://securepubads.g.doubleclick.net/gampad/ads?iu=%2F1234',
    initiatorOrigin: 'https://www.youtube.com',
    resourceType: 'xhr',
  });
  assert.equal(result.action, 'block');
  assert.equal(result.category, 'ad');
});

test('Google video transport is allowed even when a filter matches the hostname', () => {
  const result = decision({
    url: 'https://rr1---sn.example.googlevideo.com/videoplayback?range=0-999',
    initiatorOrigin: 'https://www.youtube.com',
    resourceType: 'xhr',
  });
  assert.equal(result.action, 'allow');
  assert.equal(result.reason, 'youtube-playback-safety');
  assert.equal(isProtectedPlaybackRequest({
    url: 'https://rr1---sn.example.googlevideo.com/videoplayback',
    initiatorOrigin: 'https://www.youtube.com',
    resourceType: 'other',
  }), true);
});

test('YouTube player API and media initiated by YouTube are protected', () => {
  assert.equal(decision({
    url: 'https://youtubei.googleapis.com/youtubei/v1/player?videoId=abc',
    initiatorOrigin: 'https://www.youtube.com',
    resourceType: 'xhr',
  }).action, 'allow');
  assert.equal(decision({
    url: 'https://cdn.example.test/segment',
    initiatorOrigin: 'https://www.youtube-nocookie.com',
    resourceType: 'media',
  }).action, 'allow');
});

test('YouTube main-frame navigation is never canceled by subresource rules', () => {
  assert.equal(decision({
    url: 'https://www.youtube.com/watch?v=abc',
    resourceType: 'mainFrame',
  }).action, 'allow');
});

test('the ad engine cosmetic handlers now run for YouTube while retaining origin/session checks', async () => {
  const handlers = new Map();
  const session = { webRequest: {} };
  const warnings = [];
  const ipcMain = {
    removeHandler: name => handlers.delete(name),
    handle: (name, callback) => handlers.set(name, callback),
  };
  let injected = 0;
  const controller = new ChromiumProtection({ session, ipcMain, cacheDirectory: '/tmp/cachebrowse-filter-test', logger: { info() {}, warn: message => warnings.push(message) } });
  controller.setProtection({ adBlock: true, trackerBlock: true });
  controller.adsEngine = {
    config: { enableMutationObserver: true },
    onInjectCosmeticFilters: async event => {
      injected += 1;
      await event.sender.insertCSS('body { color: inherit; }');
      await event.sender.executeJavaScript('void 0');
      return 'cosmetic-rules-applied';
    },
  };
  controller.bindCosmeticHandlers();
  const inject = handlers.get('@ghostery/adblocker/inject-cosmetic-filters');
  const mutation = handlers.get('@ghostery/adblocker/is-mutation-observer-enabled');
  const event = {
    sender: {
      session,
      insertCSS: async () => { throw Object.assign(new Error('page navigated'), { name: 'Error' }); },
      executeJavaScript: async () => { throw Object.assign(new Error('frame detached'), { name: 'Error' }); },
    },
    senderFrame: { url: 'https://www.youtube.com/watch?v=sample' },
  };
  assert.equal(await inject(event, event.senderFrame.url, {}), 'cosmetic-rules-applied');
  assert.equal(await mutation(event), true);
  assert.equal(injected, 1);
  assert.equal(warnings.length, 2);

  const crossOrigin = { ...event, senderFrame: { url: 'https://attacker.example/' } };
  assert.equal(await inject(crossOrigin, event.senderFrame.url, {}), undefined);
  assert.equal(injected, 1);
});

test('the page allowlist bypasses third-party filters for that site', () => {
  const result = decision({
    url: 'https://securepubads.g.doubleclick.net/gampad/ads',
    initiatorOrigin: 'https://shop.example.test',
    resourceType: 'script',
  }, { protection: { allowlist: ['example.test'] } });
  assert.equal(result.action, 'allow');
  assert.equal(result.reason, 'site-allowlisted');
});

test('tracker rules are categorized separately and the setting disables them', () => {
  const request = {
    url: 'https://metrics.analytics.example/collect',
    initiatorOrigin: 'https://publisher.example',
    resourceType: 'xhr',
  };
  const blocked = decision(request);
  assert.equal(blocked.action, 'block');
  assert.equal(blocked.category, 'tracker');
  assert.equal(decision(request, { protection: { trackerBlock: false } }).action, 'allow');
});

test('Java-managed custom domains and ABP path rules are parsed, not substring-matched', () => {
  assert.equal(normalizeCustomRule('*.ads.example.test'), '||ads.example.test^');
  const customAds = compileCustomRules(['ads.custom.test', '/sponsored/']);
  assert.equal(decision({
    url: 'https://cdn.ads.custom.test/resource',
    initiatorOrigin: 'https://publisher.example',
    resourceType: 'script',
  }, { adsEngine: emptyEngine, customAds }).category, 'ad');
  assert.equal(decision({
    url: 'https://cdn.example.test/sponsored/slot',
    initiatorOrigin: 'https://publisher.example',
    resourceType: 'image',
  }, { adsEngine: emptyEngine, customAds }).category, 'ad');
});

test('requests reported as other are typed from the URL before applying type-specific filters', () => {
  const scriptOnly = ElectronBlocker.parse('||ads.example.test^$script', {
    loadCosmeticFilters: false,
    loadNetworkFilters: true,
  });
  assert.equal(decision({
    url: 'https://cdn.ads.example.test/creative.js',
    initiatorOrigin: 'https://publisher.example.test',
    resourceType: 'other',
  }, { adsEngine: scriptOnly }).category, 'ad');
});

test('failed or invalid filter updates retain the active last-known-good engine', async () => {
  const controller = new ChromiumProtection({
    session: { webRequest: {} }, ipcMain: {}, cacheDirectory: '/tmp/cachebrowse-filter-test',
    logger: { info() {}, warn() {} },
  });
  const knownGood = controller.adsEngine = adsEngine;
  controller.snapshotLoaded = { ads: true, trackers: true };
  controller.loadFilterList = async () => emptyEngine;
  await controller.refreshOneList('ads');
  assert.equal(controller.adsEngine, knownGood);
  assert.equal(controller.snapshotLoaded.ads, true);
  assert.equal(controller.refreshHadFailure, true);

  controller.loadFilterList = async () => { throw new Error('test network failure'); };
  await controller.refreshOneList('ads');
  assert.equal(controller.adsEngine, knownGood);
  assert.equal(controller.refreshHadFailure, true);
});

test('one session hook protects requests from newly created tab webContents', () => {
  let beforeRequest;
  let installed = 0;
  const session = { webRequest: {
    onBeforeRequest: (_filter, listener) => { installed += 1; beforeRequest = listener; },
    onHeadersReceived: (_filter, listener) => { session.headersListener = listener; },
  } };
  const controller = new ChromiumProtection({ session, ipcMain: {}, cacheDirectory: '/tmp/cachebrowse-filter-test', logger: { info() {}, warn() {} } });
  controller.setProtection({ adBlock: true, trackerBlock: true });
  controller.bindManagedHandlers();
  for (const webContentsId of [101, 202]) {
    let blocked = false;
    beforeRequest({
      id: webContentsId, timestamp: 1, webContentsId,
      url: 'https://securepubads.g.doubleclick.net/gampad/ads',
      referrer: 'https://www.youtube.com/', resourceType: 'xhr',
    }, response => { blocked = response.cancel === true; });
    assert.equal(blocked, true);
  }
  assert.equal(installed, 1);
  assert.equal(controller.getRuntimeStatus().adBlockEnforcing, true);
});

test('the installed Chromium request handler counts canceled and redirected requests only', async () => {
  let beforeRequest;
  const session = { webRequest: {
    onBeforeRequest: (_filter, listener) => { beforeRequest = listener; },
    onHeadersReceived: (_filter, listener) => { session.headersListener = listener; },
  } };
  let reports = 0;
  const controller = new ChromiumProtection({
    session,
    ipcMain: {},
    cacheDirectory: '/tmp/cachebrowse-filter-test',
    onBlocked: async () => { reports += 1; },
    logger: { info() {}, warn() {} },
  });
  controller.adsEngine = adsEngine;
  controller.trackersEngine = trackersEngine;
  controller.fallbackAdsEngine = emptyEngine;
  controller.fallbackTrackersEngine = emptyEngine;
  controller.setProtection({ adBlock: true, trackerBlock: true, allowlist: [] });
  controller.bindManagedHandlers();

  let canceled;
  beforeRequest({
    id: 12,
    timestamp: 1,
    url: 'https://securepubads.g.doubleclick.net/gampad/ads',
    initiatorOrigin: 'https://www.youtube.com',
    referrer: 'https://www.youtube.com/',
    resourceType: 'xhr',
  }, response => { canceled = response.cancel === true; });
  assert.equal(canceled, true);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(reports, 1);

  beforeRequest({
    id: 13,
    timestamp: 2,
    url: 'https://rr1---sn.example.googlevideo.com/videoplayback',
    initiatorOrigin: 'https://www.youtube.com',
    referrer: 'https://www.youtube.com/watch?v=test',
    resourceType: 'xhr',
  }, response => { assert.deepEqual(response, {}); });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(reports, 1);

  controller.adsEngine = {
    match: () => ({
      match: true,
      filter: { rawLine: '||redirected-ad.example^' },
      redirect: { dataUrl: 'data:text/plain,blocked' },
    }),
  };
  let redirect;
  beforeRequest({
    id: 14,
    timestamp: 3,
    url: 'https://redirected-ad.example/creative.js',
    initiatorOrigin: 'https://publisher.example',
    referrer: 'https://publisher.example/',
    resourceType: 'script',
  }, response => { redirect = response.redirectURL; });
  assert.equal(redirect, 'data:text/plain,blocked');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(reports, 2);
});
