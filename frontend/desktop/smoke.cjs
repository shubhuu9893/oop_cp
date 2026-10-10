const http = require('node:http');

const debugPort = Number(process.env.CACHEBROWSE_REMOTE_DEBUGGING_PORT || 9222);
const uiPort = Number(process.env.VITE_PORT || 5173);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

class DevToolsClient {
  constructor(url) {
    this.socket = new WebSocket(url);
    this.nextId = 0;
    this.pending = new Map();
  }

  async connect() {
    await new Promise((resolve, reject) => {
      this.socket.addEventListener('open', resolve, { once: true });
      this.socket.addEventListener('error', reject, { once: true });
    });
    this.socket.addEventListener('message', event => {
      const message = JSON.parse(String(event.data));
      if (!message.id) return;
      const pending = this.pending.get(message.id);
      if (!pending) return;
      clearTimeout(pending.timer);
      this.pending.delete(message.id);
      if (message.error) pending.reject(new Error(message.error.message));
      else pending.resolve(message.result);
    });
    return this;
  }

  send(method, params = {}, timeoutMs = 35_000) {
    const id = ++this.nextId;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`DevTools timed out: ${method}`));
      }, timeoutMs);
      this.pending.set(id, { resolve, reject, timer });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  async evaluate(expression, timeoutMs = 35_000) {
    const response = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
      userGesture: true,
    }, timeoutMs);
    if (response.exceptionDetails) {
      throw new Error(response.exceptionDetails.exception?.description || 'Renderer evaluation failed');
    }
    return response.result?.value;
  }

  close() { this.socket.close(); }
}

async function getTargets() {
  const response = await fetch(`http://127.0.0.1:${debugPort}/json/list`, {
    signal: AbortSignal.timeout(3000),
  });
  if (!response.ok) throw new Error(`DevTools returned HTTP ${response.status}`);
  return response.json();
}

async function waitFor(predicate, label, timeoutMs = 25_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await predicate();
    if (value) return value;
    await sleep(300);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

async function main() {
  const targets = await waitFor(async () => {
    try { return await getTargets(); } catch { return null; }
  }, 'Electron DevTools');
  const uiTarget = targets.find(target => target.type === 'page'
    && ([`http://127.0.0.1:${uiPort}/`, `http://localhost:${uiPort}/`].some(prefix => target.url.startsWith(prefix))
      || target.url.startsWith('file:'))
    && !target.url.includes('cachebrowseOverlay=1'));
  if (!uiTarget) throw new Error('CacheBrowse React window was not found in DevTools targets');

  const ui = await new DevToolsClient(uiTarget.webSocketDebuggerUrl).connect();
  const report = { websites: [], navigation: {}, protection: {}, playback: 'not attempted' };
  try {
    const protectionRuntime = await ui.evaluate('window.cacheBrowse.getProtectionRuntimeStatus()');
    if (!protectionRuntime?.available || !protectionRuntime.requestFilteringReady) {
      throw new Error(`Chromium request filter is not enforcing: ${JSON.stringify(protectionRuntime)}`);
    }
    report.protection.runtime = protectionRuntime;
    const initialBeforeNew = await waitFor(async () => {
      const state = await ui.evaluate('window.cacheBrowse?.getTabs()');
      return state?.tabs?.length ? state : null;
    }, 'the first Chromium tab');
    await ui.evaluate(`document.querySelector('.tabbar__new').click()`);
    const initial = await waitFor(async () => {
      const state = await ui.evaluate('window.cacheBrowse.getTabs()');
      return state.tabs.length === initialBeforeNew.tabs.length + 1 && state.activeTabId !== initialBeforeNew.activeTabId ? state : null;
    }, 'the New Tab button to create and activate a tab');
    const toolbarLayout = await ui.evaluate(`(() => {
      const controls = [...document.querySelectorAll('.toolbar-nav button')];
      const expected = ['Back', 'Forward', 'Reload', 'Home'];
      const labels = controls.map(button => button.getAttribute('aria-label'));
      const bounds = controls.map(button => button.getBoundingClientRect());
      const tabList = document.querySelector('.tabbar__tabs').getBoundingClientRect();
      const newTab = document.querySelector('.tabbar__new').getBoundingClientRect();
      return {
        labels,
        visible: bounds.every(rect => rect.width > 0 && rect.height > 0),
        backDisabledInitially: controls[0]?.disabled,
        forwardDisabledInitially: controls[1]?.disabled,
        newTabAdjacent: newTab.left >= tabList.left && newTab.left - tabList.right < 12,
      };
    })()`);
    if (JSON.stringify(toolbarLayout.labels) !== JSON.stringify(['Back', 'Forward', 'Reload', 'Home'])
        || !toolbarLayout.visible || !toolbarLayout.backDisabledInitially || !toolbarLayout.forwardDisabledInitially
        || !toolbarLayout.newTabAdjacent) {
      throw new Error(`Toolbar/tab-strip layout is incorrect: ${JSON.stringify(toolbarLayout)}`);
    }
    report.toolbarLayout = toolbarLayout;
    const tabId = initial.activeTabId;

    const search = await ui.evaluate(`window.cacheBrowse.requestApi('/api/search', {
      method: 'POST', body: JSON.stringify({ query: 'cachebrowse desktop browser test' })
    })`);
    let searchTarget;
    try { searchTarget = new URL(search.body?.url); } catch { searchTarget = null; }
    const queryValues = searchTarget ? [...searchTarget.searchParams.values()].map(value => value.toLowerCase()) : [];
    if (search.status !== 200 || !searchTarget || !['http:', 'https:'].includes(searchTarget.protocol)
        || !queryValues.some(value => value.includes('cachebrowse desktop browser test'))) {
      throw new Error('Java search endpoint did not produce a valid configured-provider destination');
    }
    await ui.evaluate(`(async () => { await window.cacheBrowse.navigate(${JSON.stringify(tabId)}, ${JSON.stringify(search.body.url)}); return true; })()`, 5000);
    const searchPage = await waitFor(async () => {
      const state = await ui.evaluate('window.cacheBrowse.getTabs()');
      const active = state.tabs.find(item => item.id === tabId);
      return active?.url.startsWith(searchTarget.origin)
        && active.title && active.title !== 'New Tab' ? active : null;
    }, `real ${searchTarget.hostname} search results`);
    report.search = { status: search.status, provider: searchTarget.hostname, target: search.body.url, renderedTitle: searchPage.title };

    async function navigate(url, expectedHost) {
      const serializedId = JSON.stringify(tabId);
      await ui.evaluate(`(async () => { await window.cacheBrowse.navigate(${serializedId}, ${JSON.stringify(url)}); return true; })()`, 5000);
      const tab = await waitFor(async () => {
        const state = await ui.evaluate('window.cacheBrowse.getTabs()');
        const active = state.tabs.find(item => item.id === tabId);
        if (active?.error) throw new Error(active.error);
        if (!active || !active.url) return null;
        const host = new URL(active.url).hostname;
        const reachedHost = host === expectedHost || host.endsWith(`.${expectedHost}`);
        return reachedHost && active.title && active.title !== 'New Tab' ? active : null;
      }, `${expectedHost} to finish in Chromium`);
      if (!tab.title || tab.title === 'New Tab') throw new Error(`${expectedHost} did not expose a page title`);
      report.websites.push({ host: expectedHost, title: tab.title, url: tab.url, loadingAtCheck: tab.loading });
      return tab;
    }

    async function navigateInTab(targetTabId, url, expectedHost) {
      await ui.evaluate(`window.cacheBrowse.navigate(${JSON.stringify(targetTabId)}, ${JSON.stringify(url)})`, 5000);
      return waitFor(async () => {
        const state = await ui.evaluate(`window.cacheBrowse.getTabs().then(value => value.tabs.find(tab => tab.id === ${JSON.stringify(targetTabId)}))`);
        if (state?.error) throw new Error(state.error);
        if (!state?.url || !state.title || state.loading || state.title === 'New Tab') return null;
        return new URL(state.url).hostname.endsWith(expectedHost) ? state : null;
      }, `${expectedHost} in the secondary tab`);
    }

    await navigate('https://example.com/', 'example.com');
    const wikipedia = await navigate('https://www.wikipedia.org/', 'wikipedia.org');

    await ui.evaluate(`document.querySelector('.toolbar-nav button[aria-label="Back"]').click()`);
    const backState = await waitFor(async () => {
      const state = await ui.evaluate('window.cacheBrowse.getTabs()');
      const tab = state.tabs.find(item => item.id === tabId);
      const controls = await ui.evaluate(`({ back: document.querySelector('.toolbar-nav button[aria-label="Back"]').disabled, forward: document.querySelector('.toolbar-nav button[aria-label="Forward"]').disabled })`);
      return tab && new URL(tab.url).hostname === 'example.com' && !controls.back && !controls.forward ? tab : null;
    }, 'back navigation');
    if (!backState.canGoForward) throw new Error('Forward was not enabled after going back');
    await ui.evaluate(`document.querySelector('.toolbar-nav button[aria-label="Forward"]').click()`);
    const forwardState = await waitFor(async () => {
      const state = await ui.evaluate('window.cacheBrowse.getTabs()');
      const tab = state.tabs.find(item => item.id === tabId);
      const controls = await ui.evaluate(`({ back: document.querySelector('.toolbar-nav button[aria-label="Back"]').disabled, forward: document.querySelector('.toolbar-nav button[aria-label="Forward"]').disabled })`);
      return tab && new URL(tab.url).hostname.endsWith('wikipedia.org') && !controls.back && controls.forward ? tab : null;
    }, 'forward navigation');
    if (forwardState.canGoForward) throw new Error('Forward remained enabled at the end of tab history');
    await ui.evaluate(`document.querySelector('.toolbar-nav button[aria-label="Reload"]').click()`);
    await waitFor(async () => {
      const state = await ui.evaluate('window.cacheBrowse.getTabs()');
      const tab = state.tabs.find(item => item.id === tabId);
      return tab && !tab.loading && new URL(tab.url).hostname.endsWith('wikipedia.org');
    }, 'reload');
    report.navigation = { back: backState.url, forward: forwardState.url, reload: 'completed' };

    const remainingSites = [
      ['https://www.google.com/', 'google.com'],
      ['https://duckduckgo.com/', 'duckduckgo.com'],
      ['https://github.com/', 'github.com'],
      ['https://www.youtube.com/', 'youtube.com'],
    ];
    for (const [url, host] of remainingSites) await navigate(url, host);

    const youtubeTarget = (await getTargets()).find(target => target.type === 'page'
      && (() => { try { return new URL(target.url).hostname.endsWith('youtube.com'); } catch { return false; } })());
    if (youtubeTarget) {
      const youtube = await new DevToolsClient(youtubeTarget.webSocketDebuggerUrl).connect();
      try {
        const videoUrl = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';
        await ui.evaluate(`(async () => { await window.cacheBrowse.navigate(${JSON.stringify(tabId)}, ${JSON.stringify(videoUrl)}); return true; })()`, 5000);
        await waitFor(async () => {
          const state = await ui.evaluate('window.cacheBrowse.getTabs()');
          const tab = state.tabs.find(item => item.id === tabId);
          return tab && tab.url.includes('youtube.com/watch') && tab.title !== 'New Tab';
        }, 'YouTube video page', 35_000);
        const player = await waitFor(async () => youtube.evaluate(`(() => {
          const video = document.querySelector('video');
          return video ? { found: true, readyState: video.readyState, width: video.videoWidth } : null;
        })()`), 'YouTube video element', 20_000).catch(() => null);
        if (!player) {
          report.playback = await youtube.evaluate(`({
            found: false,
            title: document.title,
            readyState: document.readyState,
            playerError: document.querySelector('.ytp-error')?.innerText || null
          })`);
        } else {
          report.playback = await youtube.evaluate(`(() => {
            const video = document.querySelector('video');
            video.muted = true;
            return video.play().then(() => ({ found: true, paused: video.paused, width: video.videoWidth, readyState: video.readyState }))
              .catch(error => ({ found: true, paused: video.paused, error: error.name, width: video.videoWidth, readyState: video.readyState }));
          })()`);
        }
      } finally { youtube.close(); }
    }

    const tabsBefore = await ui.evaluate('window.cacheBrowse.getTabs()');
    const extraTabId = await ui.evaluate('window.cacheBrowse.newTab()');
    const tabsWithNew = await ui.evaluate('window.cacheBrowse.getTabs()');
    if (tabsWithNew.tabs.length !== tabsBefore.tabs.length + 1) throw new Error('New tab was not created');
    await navigateInTab(extraTabId, 'https://example.com/', 'example.com');
    await navigateInTab(extraTabId, 'https://www.wikipedia.org/', 'wikipedia.org');
    let extraState = await ui.evaluate(`window.cacheBrowse.getTabs().then(state => state.tabs.find(tab => tab.id === ${JSON.stringify(extraTabId)}))`);
    if (!extraState?.canGoBack) throw new Error('The new tab did not expose its own back history');
    await ui.evaluate(`window.cacheBrowse.activateTab(${JSON.stringify(tabId)})`);
    const originalState = await ui.evaluate(`window.cacheBrowse.getTabs().then(state => state.tabs.find(tab => tab.id === ${JSON.stringify(tabId)}))`);
    if (!originalState?.url.includes('youtube.com/watch') || !originalState.canGoBack) throw new Error('Switching tabs lost the original tab navigation state');
    await ui.evaluate(`window.cacheBrowse.activateTab(${JSON.stringify(extraTabId)})`);
    await ui.evaluate(`window.cacheBrowse.back(${JSON.stringify(extraTabId)})`);
    extraState = await waitFor(async () => {
      const state = await ui.evaluate(`window.cacheBrowse.getTabs().then(value => value.tabs.find(tab => tab.id === ${JSON.stringify(extraTabId)}))`);
      return state && new URL(state.url).hostname === 'example.com' && state.canGoForward ? state : null;
    }, 'independent tab back/forward state');
    report.tabsNavigation = { activeTabBack: originalState.canGoBack, secondaryTabBack: true, secondaryTabForwardAfterBack: extraState.canGoForward };
    await ui.evaluate(`window.cacheBrowse.activateTab(${JSON.stringify(tabId)})`);
    await ui.evaluate(`window.cacheBrowse.closeTab(${JSON.stringify(extraTabId)})`);
    const tabsAfterClose = await ui.evaluate('window.cacheBrowse.getTabs()');
    if (tabsAfterClose.tabs.some(tab => tab.id === extraTabId)) throw new Error('Closed tab still exists');
    report.tabs = { opened: true, switched: tabsAfterClose.activeTabId === tabId, closed: true };

    const pageTarget = (await getTargets()).find(target => target.type === 'page'
      && (() => { try { return new URL(target.url).hostname === 'www.youtube.com'; } catch { return false; } })());
    if (pageTarget) {
      const page = await new DevToolsClient(pageTarget.webSocketDebuggerUrl).connect();
      try {
        await ui.evaluate(`document.querySelector('.protection-trigger').click()`);
        const popupTarget = await waitFor(async () => {
          const list = await getTargets();
          return list.find(target => target.type === 'page' && target.url.includes('cachebrowseOverlay=1')) || null;
        }, 'the React native-popup overlay renderer');
        const popupUi = await new DevToolsClient(popupTarget.webSocketDebuggerUrl).connect();
        const pageBoundsBeforePopup = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
        const popup = await waitFor(async () => popupUi.evaluate(`(() => {
          const element = document.querySelector('.native-popup-overlay__panel');
          if (!element) return null;
          const rect = element.getBoundingClientRect();
          return { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right, height: rect.height, viewportWidth: innerWidth, viewportHeight: innerHeight, text: element.innerText };
        })()`), 'protection popup over the webpage');
        const pageBoundsWithPopup = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
        if (!popup.text.includes('Ad Blocking') || !popup.text.includes('Tracker Blocking')
            || popup.top < 0 || popup.left < 0 || popup.bottom > popup.viewportHeight || popup.right > popup.viewportWidth
            || JSON.stringify(pageBoundsBeforePopup.page) !== JSON.stringify(pageBoundsWithPopup.page)
            || !pageBoundsWithPopup.popup) {
          throw new Error(`Protection overlay changed the webpage bounds or did not render: ${JSON.stringify({ popup, pageBoundsBeforePopup, pageBoundsWithPopup })}`);
        }
        await popupUi.evaluate(`window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))`);
        await waitFor(async () => ui.evaluate(`!document.querySelector('.protection-popup')`), 'protection popup to close');
        const pageBoundsAfterClose = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
        if (JSON.stringify(pageBoundsBeforePopup.page) !== JSON.stringify(pageBoundsAfterClose.page)
            || pageBoundsAfterClose.overlayAttached || pageBoundsAfterClose.overlayVisible) {
          throw new Error(`Popup close left a native overlay surface attached or changed page bounds: ${JSON.stringify(pageBoundsAfterClose)}`);
        }
        await ui.evaluate(`window.cacheBrowse.showPopupOverlay({ id: 'smoke-switch-check', kind: 'protection', payload: { adBlock: true, trackerBlock: true }, bounds: { x: 24, y: 100, width: 280, height: 260 } })`);
        await waitFor(async () => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => Boolean(state.popup))'), 'popup before tab switch');
        await ui.evaluate(`window.cacheBrowse.activateTab(${JSON.stringify(initialBeforeNew.activeTabId)})`);
        await waitFor(async () => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popup === null)'), 'popup cleanup on tab switch');
        await ui.evaluate(`window.cacheBrowse.activateTab(${JSON.stringify(tabId)})`);
        popupUi.close();
        report.popupLayer = { protectionMenuVisible: true, webpageBoundsUnchanged: true, escapeCloses: true, nativeOverlayDetachedOnClose: true, viewportClamped: true, tabSwitchClosesPopup: true };

        const hasBookmarksMenu = await ui.evaluate("Boolean(document.querySelector('.bookmarks-bar__all'))");
        if (hasBookmarksMenu) {
          const beforeBookmarks = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
          await ui.evaluate("document.querySelector('.bookmarks-bar__all').click()");
          const bookmarksTarget = await waitFor(async () => (await getTargets()).find(target => target.type === 'page' && target.url.includes('cachebrowseOverlay=1')) || null, 'bookmarks overlay renderer');
          const bookmarksUi = new DevToolsClient(bookmarksTarget.webSocketDebuggerUrl);
          await bookmarksUi.connect();
          const bookmarksPopup = await waitFor(async () => bookmarksUi.evaluate(`(() => {
            const panel = document.querySelector('.bookmarks-popover');
            return panel ? panel.innerText : null;
          })()`), 'bookmarks popover above Chromium');
          const duringBookmarks = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
          if (!bookmarksPopup.includes('Bookmarks') || JSON.stringify(beforeBookmarks.page) !== JSON.stringify(duringBookmarks.page)) {
            throw new Error('Bookmarks popover did not overlay without changing webpage bounds');
          }
          await bookmarksUi.evaluate(`document.querySelector('.native-popup-overlay').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))`);
          await waitFor(async () => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popup === null)'), 'outside-click dismissal of bookmarks');
          bookmarksUi.close();
          report.popupLayer.bookmarksOutsideClick = true;
        }
        const hasProfile = await ui.evaluate("Boolean(document.querySelector('.user-avatar-btn'))");
        if (hasProfile) {
          await ui.evaluate("document.querySelector('.user-avatar-btn').click()");
          const profileTarget = await waitFor(async () => (await getTargets()).find(target => target.type === 'page' && target.url.includes('cachebrowseOverlay=1')) || null, 'profile overlay renderer');
          const profileUi = new DevToolsClient(profileTarget.webSocketDebuggerUrl);
          await profileUi.connect();
          const profileText = await waitFor(async () => profileUi.evaluate("document.querySelector('.user-dropdown')?.innerText || null"), 'profile dropdown above Chromium');
          if (!profileText.includes('Dashboard') || !profileText.includes('Settings') || !profileText.includes('Sign Out')) {
            throw new Error(`Profile actions are missing from the popup: ${profileText}`);
          }
          await profileUi.evaluate("window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))");
          await waitFor(async () => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popup === null)'), 'profile Escape dismissal');
          profileUi.close();
          report.popupLayer.profileMenu = 'visible; Dashboard, Settings, and Sign Out present';
        } else {
          report.popupLayer.profileMenu = 'not runtime-tested: no authenticated profile in this session';
        }

        const before = await ui.evaluate("window.cacheBrowse.requestApi('/api/protection/statistics')");
        await page.evaluate("fetch('https://doubleclick.net/?cachebrowse-smoke=1').catch(() => 'cancelled')", 10_000);
        const after = await waitFor(async () => {
          const result = await ui.evaluate("window.cacheBrowse.requestApi('/api/protection/statistics')");
          return result.body?.adsBlocked > before.body?.adsBlocked ? result.body : null;
        }, 'a real Chromium ad-request cancellation', 8_000);
        report.protection = { ...report.protection, before: before.body.adsBlocked, after: after.adsBlocked, counted: true };
      } finally { page.close(); }
    } else {
      report.protection = { counted: false, reason: 'YouTube Chromium target not available for request test' };
    }

    report.chromiumCacheBytes = await ui.evaluate('window.cacheBrowse.getBrowserCacheSize()');

    const signInButton = await ui.evaluate("document.querySelector('.user-login-btn') ? true : false");
    if (signInButton) {
      await ui.evaluate("document.querySelector('.user-login-btn').click()");
      await waitFor(async () => ui.evaluate("Boolean(document.querySelector('.auth-overlay'))"), 'sign-in overlay');
      const whileAuthOpen = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
      if (whileAuthOpen.activePageVisible) throw new Error('Chromium page stayed interactive above the sign-in modal');
      await ui.evaluate("document.querySelector('.auth-overlay__close').click()");
      await waitFor(async () => ui.evaluate("!document.querySelector('.auth-overlay') && window.cacheBrowse.getLayoutMetrics().then(state => state.activePageVisible && !state.overlayAttached)"), 'auth modal close to release native layers');
      report.authOverlay = { modalUnmounted: true, chromiumSurfaceRestored: true, firebaseSignIn: 'not verified; no test credentials supplied' };
    }

    const serverTimers = new Set();
    const slowServer = http.createServer((_request, response) => {
      const timer = setTimeout(() => {
        serverTimers.delete(timer);
        if (!response.destroyed) response.end('<!doctype html><title>stop-test</title>');
      }, 15_000);
      serverTimers.add(timer);
    });
    await new Promise((resolve, reject) => {
      slowServer.once('error', reject);
      slowServer.listen(0, '127.0.0.1', resolve);
    });
    try {
      const slowUrl = `http://127.0.0.1:${slowServer.address().port}/slow`;
      await ui.evaluate(`(async () => { await window.cacheBrowse.navigate(${JSON.stringify(tabId)}, ${JSON.stringify(slowUrl)}); return true; })()`, 5000);
      await waitFor(async () => {
        const state = await ui.evaluate('window.cacheBrowse.getTabs()');
        return state.tabs.find(item => item.id === tabId)?.loading;
      }, 'slow page loading');
      const stopLabel = await ui.evaluate(`document.querySelector('.toolbar-nav button[aria-label="Stop loading"]')?.getAttribute('aria-label')`);
      if (stopLabel !== 'Stop loading') throw new Error('Stop control did not appear while navigating');
      await ui.evaluate(`document.querySelector('.toolbar-nav button[aria-label="Stop loading"]').click()`);
      const stopped = await waitFor(async () => {
        const state = await ui.evaluate('window.cacheBrowse.getTabs()');
        const tab = state.tabs.find(item => item.id === tabId);
        return tab && !tab.loading;
      }, 'stop loading');
      report.navigation.stop = !stopped.loading;
    } finally {
      for (const timer of serverTimers) clearTimeout(timer);
      slowServer.closeAllConnections();
      await new Promise(resolve => slowServer.close(resolve));
    }

    console.log(JSON.stringify(report, null, 2));
    const siteFailures = report.websites.length !== 6;
    if (siteFailures || !report.navigation.stop || !report.protection.counted
        || !report.protection.runtime?.requestFilteringReady) process.exitCode = 1;
  } finally {
    ui.close();
  }
}

main().catch(error => {
  console.error(`CacheBrowse desktop smoke test failed: ${error.stack || error.message}`);
  process.exitCode = 1;
});
