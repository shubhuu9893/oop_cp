const debugPort = Number(process.env.CACHEBROWSE_REMOTE_DEBUGGING_PORT || 9222);
const uiPort = Number(process.env.VITE_PORT || 5173);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

class DevTools {
  constructor(url) { this.socket = new WebSocket(url); this.sequence = 0; this.pending = new Map(); }
  async connect() {
    await new Promise((resolve, reject) => {
      this.socket.addEventListener('open', resolve, { once: true });
      this.socket.addEventListener('error', reject, { once: true });
    });
    this.socket.addEventListener('message', event => {
      const message = JSON.parse(String(event.data));
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      clearTimeout(pending.timer);
      if (message.error) pending.reject(new Error(message.error.message)); else pending.resolve(message.result);
    });
    return this;
  }
  send(method, params = {}) {
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error(`Timeout: ${method}`)); }, 10_000);
      this.pending.set(id, { resolve, reject, timer });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }
  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true });
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || 'Renderer error');
    return result.result?.value;
  }
  close() { this.socket.close(); }
}

async function eventually(check, label) {
  const end = Date.now() + 15_000;
  while (Date.now() < end) {
    const value = await check();
    if (value) return value;
    await delay(150);
  }
  throw new Error(`Timed out: ${label}`);
}

async function pointAt(ui, selector) {
  return ui.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e)return null;const r=e.getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2}})()`);
}

async function clickPoint(target, point) {
  await target.send('Input.dispatchMouseEvent', { type: 'mousePressed', ...point, button: 'left', clickCount: 1 });
  await target.send('Input.dispatchMouseEvent', { type: 'mouseReleased', ...point, button: 'left', clickCount: 1 });
}

async function main() {
  const targets = await eventually(async () => {
    try {
      const response = await fetch(`http://127.0.0.1:${debugPort}/json/list`);
      return response.ok ? response.json() : null;
    } catch { return null; }
  }, 'Electron DevTools');
  const uiTarget = targets.find(target => target.type === 'page' && target.url.startsWith(`http://localhost:${uiPort}/`) && !target.url.includes('cachebrowseOverlay=1'));
  if (!uiTarget) throw new Error('CacheBrowse UI target not found');
  const ui = await new DevTools(uiTarget.webSocketDebuggerUrl).connect();
  let overlay;
  let pageContents;
  let temporaryTabId;
  let toolbarPointerDelivered = false;
  let tabPointerDelivered = false;
  let shieldClickToggle = false;
  let avatarClickToggle = 'skipped: no authenticated profile';
  let escapeDismissed = false;
  let outsideDismissed = false;
  const initialTabs = await ui.evaluate('window.cacheBrowse.getTabs()');
  const originalTabId = initialTabs.activeTabId;
  try {
    await eventually(() => ui.evaluate("Boolean(document.querySelector('.browser-toolbar') && document.querySelector('.protection-trigger'))"), 'React toolbar initialized');
    const before = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
    await ui.evaluate(`window.cacheBrowse.showPopupOverlay({ id: 'overlay-regression', kind: 'protection', payload: { adBlock: true, trackerBlock: true, status: 'Test state' }, bounds: { x: 24, y: 80, width: 280, height: 220 } })`);
    const visible = await eventually(async () => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.overlayVisible && state.overlayAttached && state.popup ? state : null)'), 'popup overlay visible');
    const overlayBounds = visible.overlayBounds;
    if (!overlayBounds || JSON.stringify(overlayBounds) !== JSON.stringify(visible.popup)
        || (overlayBounds.x === 0 && overlayBounds.y === 0 && overlayBounds.width === visible.window[0] && overlayBounds.height === visible.window[1])) {
      throw new Error(`Native popup view is not constrained to the popup: ${JSON.stringify(visible)}`);
    }
    const overlayTarget = (await fetch(`http://127.0.0.1:${debugPort}/json/list`).then(response => response.json())).find(target => target.type === 'page' && target.url.includes('cachebrowseOverlay=1'));
    overlay = await new DevTools(overlayTarget.webSocketDebuggerUrl).connect();
    const popupText = await eventually(() => overlay.evaluate("document.querySelector('.native-popup-overlay__panel')?.innerText || null"), 'protection popup content');
    if (!popupText.includes('Ad Blocking') || !popupText.includes('Tracker Blocking')) throw new Error('Protection popup content missing');
    const during = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
    if (JSON.stringify(before.page) !== JSON.stringify(during.page)) throw new Error('Popup changed Chromium page bounds');
    await ui.evaluate("window.cacheBrowse.closePopupOverlay('overlay-regression')");
    const closed = await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => !state.overlayVisible && !state.overlayAttached && !state.popup)'), 'native overlay detached');

    const shieldPoint = await pointAt(ui, '.protection-trigger');
    if (!shieldPoint) throw new Error('Shield control was not present after toolbar initialization');
    for (let cycle = 0; cycle < 5; cycle++) {
      await clickPoint(ui, shieldPoint);
      await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popupKind === "protection" && document.querySelector(".protection-trigger").getAttribute("aria-expanded") === "true")'), `shield opens, cycle ${cycle + 1}`);
      await clickPoint(ui, shieldPoint);
      await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => !state.popup && document.querySelector(".protection-trigger").getAttribute("aria-expanded") === "false")'), `shield closes, cycle ${cycle + 1}`);
    }
    shieldClickToggle = true;
    await clickPoint(ui, shieldPoint);
    await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popupKind === "protection")'), 'reopen protection popup for outside dismissal');
    await ui.evaluate("window.__cacheBrowsePointerEvents=[]; addEventListener('pointerdown', event => window.__cacheBrowsePointerEvents.push({ x: event.clientX, y: event.clientY, target: event.target?.className || event.target?.tagName }), true)");
    const addressPoint = await pointAt(ui, '.address-bar__input');
    await clickPoint(ui, addressPoint);
    const pointerEvents = await eventually(() => ui.evaluate('window.__cacheBrowsePointerEvents?.length ? window.__cacheBrowsePointerEvents : null'), 'address bar pointer event with protection overlay open');
    if (!pointerEvents.some(event => event.target.includes('address'))) throw new Error(`Address bar did not receive pointer input: ${JSON.stringify(pointerEvents)}`);
    toolbarPointerDelivered = true;
    await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => !state.popup)'), 'address click closes protection popup');
    outsideDismissed = true;
    await clickPoint(ui, shieldPoint);
    await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popupKind === "protection")'), 'reopen protection popup for Escape test');
    await overlay.send('Input.dispatchKeyEvent', { type: 'rawKeyDown', key: 'Escape', windowsVirtualKeyCode: 27 });
    await overlay.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', windowsVirtualKeyCode: 27 });
    await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => !state.popup)'), 'Escape closes protection popup');
    escapeDismissed = true;

    const avatarPoint = await pointAt(ui, '.user-avatar-btn');
    if (avatarPoint) {
      for (let cycle = 0; cycle < 5; cycle++) {
        await clickPoint(ui, avatarPoint);
        await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popupKind === "profile" && document.querySelector(".user-avatar-btn").getAttribute("aria-expanded") === "true")'), `avatar opens, cycle ${cycle + 1}`);
        await clickPoint(ui, avatarPoint);
        await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => !state.popup && document.querySelector(".user-avatar-btn").getAttribute("aria-expanded") === "false")'), `avatar closes, cycle ${cycle + 1}`);
      }
      avatarClickToggle = 'five repeated open/close cycles passed';
      await clickPoint(ui, shieldPoint);
      await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popupKind === "protection")'), 'protection open before switching popup');
      await clickPoint(ui, avatarPoint);
      await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popupKind === "profile")'), 'profile replaces protection popup');
      await clickPoint(ui, addressPoint);
      await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => !state.popup && document.querySelector(".user-avatar-btn").getAttribute("aria-expanded") === "false")'), 'outside address click closes profile menu');
      await clickPoint(ui, avatarPoint);
      await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.popupKind === "profile")'), 'reopen profile menu for Escape check');
      await overlay.send('Input.dispatchKeyEvent', { type: 'rawKeyDown', key: 'Escape', windowsVirtualKeyCode: 27 });
      await overlay.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', windowsVirtualKeyCode: 27 });
      await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => !state.popup)'), 'Escape closes profile menu');
    }

    temporaryTabId = await ui.evaluate("window.cacheBrowse.newTab('https://example.com/')");
    await eventually(() => ui.evaluate(`window.cacheBrowse.getTabs().then(state => state.tabs.find(tab => tab.id === ${JSON.stringify(temporaryTabId)})?.url === 'https://example.com/')`), 'temporary real page for post-auth interaction checks');
    const pageTarget = (await fetch(`http://127.0.0.1:${debugPort}/json/list`).then(response => response.json())).find(target => target.type === 'page' && target.url.startsWith('https://example.com/'));
    if (!pageTarget) throw new Error('Temporary Chromium page target not found');
    pageContents = await new DevTools(pageTarget.webSocketDebuggerUrl).connect();
    const nativePage = await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => state.activePageVisible && state.activePageBounds?.width > 0 && state.page.width > 0 ? state : null)'), 'active page native bounds');
    if (['x', 'y', 'width', 'height'].some(key => Math.abs(nativePage.page[key] - nativePage.activePageBounds[key]) > 1)) {
      throw new Error(`Native page bounds disagree with React viewport: ${JSON.stringify(nativePage)}`);
    }
    await eventually(() => pageContents.evaluate("document.readyState === 'complete' && Boolean(document.querySelector('a'))"), 'test website content');
    const pageAnchor = await pageContents.evaluate("(()=>{const a=document.querySelector('a');if(!a)return null;a.scrollIntoView({block:'center'});const r=a.getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2,target:document.elementFromPoint(r.left+r.width/2,r.top+r.height/2)?.tagName}})()");
    if (!pageAnchor) throw new Error('Temporary website has no test link');
    if (pageAnchor.target !== 'A') throw new Error(`Test page link is not hit-testable at its center: ${JSON.stringify(pageAnchor)}`);
    const webpageContent = await pageContents.evaluate("({ title: document.title, text: document.body?.innerText?.trim().slice(0, 120) || '' })");
    if (!webpageContent.text) throw new Error(`Actual test website has no rendered text: ${JSON.stringify(webpageContent)}`);

    const tabPoint = await pointAt(ui, '.tabbar__tab:not(.active)');
    if (tabPoint) {
      await clickPoint(ui, tabPoint);
      await eventually(() => ui.evaluate(`window.cacheBrowse.getTabs().then(state => state.activeTabId === ${JSON.stringify(originalTabId)})`), 'switch to original tab by tab-strip pointer');
      tabPointerDelivered = true;
      await ui.evaluate(`window.cacheBrowse.activateTab(${JSON.stringify(temporaryTabId)})`);
      await eventually(() => ui.evaluate(`window.cacheBrowse.getTabs().then(state => state.activeTabId === ${JSON.stringify(temporaryTabId)})`), 'restore test tab');
    }

    const hasProfile = await ui.evaluate("Boolean(document.querySelector('.user-avatar-btn'))");
    let profileMenu = 'not exercised: no authenticated profile is present';
    if (hasProfile) {
      const menuAlreadyOpen = await ui.evaluate("document.querySelector('.user-avatar-btn').getAttribute('aria-expanded') === 'true'");
      if (menuAlreadyOpen) {
        await ui.evaluate("document.querySelector('.user-avatar-btn').click()");
        await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => !state.popup)'), 'close pre-existing profile menu');
      }
      const profileBoundsBefore = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
      await ui.evaluate("document.querySelector('.user-avatar-btn').click()");
      await eventually(() => ui.evaluate('window.cacheBrowse.getLayoutMetrics().then(state => Boolean(state.popup))'), 'profile menu state');
      const profileText = await eventually(() => overlay.evaluate("document.querySelector('.user-dropdown')?.innerText || null"), 'profile menu content');
      if (!profileText.includes('Dashboard') || !profileText.includes('Settings') || !profileText.includes('Sign Out')) {
        throw new Error(`Profile popup actions are missing: ${profileText}`);
      }
      const profileBounds = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
      if (JSON.stringify(profileBoundsBefore.page) !== JSON.stringify(profileBounds.page)) throw new Error(`Profile menu changed page bounds: ${JSON.stringify({ before: profileBoundsBefore.page, after: profileBounds.page })}`);
      profileMenu = 'actual signed-in profile menu rendered; Dashboard, Settings, Sign Out present';

      const dashboardPoint = await overlay.evaluate("(()=>{const item=document.querySelector('.user-dropdown__item');const r=item.getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2}})()");
      await overlay.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: dashboardPoint.x, y: dashboardPoint.y, button: 'left', clickCount: 1 });
      await overlay.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: dashboardPoint.x, y: dashboardPoint.y, button: 'left', clickCount: 1 });
      await eventually(() => ui.evaluate("Boolean(document.querySelector('.dashboard-page'))"), 'Dashboard action from profile menu');
      await ui.evaluate("document.querySelector('.toolbar-actions button[aria-label=\"Settings\"]').click()");
      await eventually(() => ui.evaluate("Boolean(document.querySelector('.settings-page'))"), 'Settings toolbar action');
      await ui.evaluate("document.querySelector('.toolbar-actions button[aria-label=\"Dashboard\"]').click()");
      await eventually(() => ui.evaluate("Boolean(document.querySelector('.dashboard-page'))"), 'return to dashboard panel');
      await ui.evaluate("document.querySelector('.toolbar-actions button[aria-label=\"Dashboard\"]').click()");
      await eventually(() => ui.evaluate("window.cacheBrowse.getLayoutMetrics().then(state => state.activePageVisible && !document.querySelector('.settings-page') && !document.querySelector('.dashboard-page'))"), 'return to browser after panel navigation');
    }
    const hasLogin = await ui.evaluate("Boolean(document.querySelector('.user-login-btn'))");
    let authModal = 'not exercised: an authenticated profile is present';
    if (hasLogin) {
      await ui.evaluate("document.querySelector('.user-login-btn').click()");
      await eventually(() => ui.evaluate("Boolean(document.querySelector('.auth-overlay'))"), 'auth modal visible');
      const modalState = await ui.evaluate('window.cacheBrowse.getLayoutMetrics()');
      if (modalState.activePageVisible || modalState.overlayAttached) throw new Error('Auth modal left a native surface over the app');
      await ui.evaluate("document.querySelector('.auth-overlay__close').click()");
      await eventually(() => ui.evaluate("!document.querySelector('.auth-overlay') && window.cacheBrowse.getLayoutMetrics().then(state => state.activePageVisible && !state.overlayAttached)"), 'auth modal removed and page restored');
      authModal = 'opened/closed; native overlay detached and Chromium surface restored';
    }
    console.log(JSON.stringify({ popupVisible: true, requestedPopupBounds: visible.popup, actualNativePopupViewBounds: visible.overlayBounds, nativePopupBoundsMatchRequest: JSON.stringify(visible.popup) === JSON.stringify(visible.overlayBounds), shieldClickToggle, avatarClickToggle, outsideDismissed, escapeDismissed, nativePageBoundsMatchReactViewport: true, webpageContentLoadedInChromium: webpageContent, webpageLinkHitTestTarget: pageAnchor.target, toolbarPointerDelivered, tabPointerDelivered, pageBoundsUnchanged: true, overlayDetachedOnClose: Boolean(closed), profileMenu, authenticatedNavigation: hasProfile ? 'Dashboard and Settings actions worked; browser surface restored' : 'not tested without an authenticated profile', authModal, firebaseCredentialLogin: 'not tested; no credentials supplied' }, null, 2));
  } finally {
    if (temporaryTabId) {
      await ui.evaluate(`window.cacheBrowse.closeTab(${JSON.stringify(temporaryTabId)})`).catch(() => {});
      if (originalTabId) await ui.evaluate(`window.cacheBrowse.activateTab(${JSON.stringify(originalTabId)})`).catch(() => {});
    }
    await ui.evaluate('window.cacheBrowse.closePopupOverlay()').catch(() => {});
    pageContents?.close();
    overlay?.close();
    ui.close();
  }
}

main().catch(error => { console.error(`Overlay regression test failed: ${error.stack || error.message}`); process.exitCode = 1; });
