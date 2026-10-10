import React, { useCallback, useEffect, useState } from 'react';
import { useAuth } from './hooks/useAuth.js';
import { useTheme } from './hooks/useTheme.js';
import { useTabs } from './hooks/useTabs.js';
import { useStats } from './hooks/useStats.js';
import { useBrowser } from './hooks/useBrowser.js';
import { bookmarkApi, healthApi, historyApi } from './services/api.js';
import LoginPage from './pages/LoginPage.jsx';

import BrowserToolbar from './components/BrowserToolbar.jsx';
import BrowserViewport from './components/BrowserViewport.jsx';
import TabBar from './components/TabBar.jsx';
import StatusBar from './components/StatusBar.jsx';
import HomePage from './pages/HomePage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import HistoryPage from './pages/HistoryPage.jsx';
import BookmarksPage from './pages/BookmarksPage.jsx';
import BookmarksBar from './components/BookmarksBar.jsx';
import DownloadsPage from './pages/DownloadsPage.jsx';
import SettingsPage from './pages/SettingsPage.jsx';
import AdminDashboardPage from './pages/AdminDashboardPage.jsx';
import './styles/app.css';

export default function App() {
  useTheme();
  const { user, login, register, loginGoogle, resetPassword, logout, configError: firebaseConfigError } = useAuth();
  const { tabs, activeTab, activeTabId, addTab, closeTab, switchTab, duplicateTab } = useTabs();
  const { stats, protection, error: statsError, toggleAdBlock, toggleTrackerBlock, refetch: refreshStats } = useStats(5000);
  const browser = useBrowser(activeTab);
  const [panel, setPanel] = useState('browser');
  const [statusText, setStatusText] = useState('Ready');
  const [authOpen, setAuthOpen] = useState(false);
  const [authIntent, setAuthIntent] = useState(null);
  const [backendStatus, setBackendStatus] = useState(() => ({
    online: false,
    starting: Boolean(window.cacheBrowse),
  }));
  const [bookmarks, setBookmarks] = useState([]);
  const [bookmarkBarVisible, setBookmarkBarVisible] = useState(() => {
    try { return localStorage.getItem('cachebrowse.bookmark-bar') !== 'hidden'; } catch { return true; }
  });
  const [bookmarkRequest, setBookmarkRequest] = useState(0);
  const refreshBookmarks = useCallback(async () => {
    if (!user?.uid) { setBookmarks([]); return []; }
    try {
      const items = await bookmarkApi.getAll();
      setBookmarks(items);
      return items;
    }
    catch (error) { console.warn(`[Bookmarks] Could not load bookmarks: ${error.message}`); }
  }, [user?.uid]);

  useEffect(() => { refreshBookmarks(); }, [refreshBookmarks]);

  // Firebase can confirm a session independently of the form promise (for
  // example after redirect sign-in). Once the auth listener supplies a UID,
  // discard any stale sign-in backdrop and release the native browser surface.
  useEffect(() => {
    if (!user?.uid) return;
    setAuthOpen(false);
  }, [user?.uid]);

  useEffect(() => {
    if (!user?.uid || !window.cacheBrowse) return undefined;
    return window.cacheBrowse.subscribe('tabEvent', payload => {
      if (payload?.event !== 'navigate' || !/^https?:/i.test(payload.tab?.url || '')) return;
      historyApi.visit(payload.tab.url, payload.tab.title || payload.tab.url).catch(error => {
        console.warn(`[History] Visit was not persisted: ${error.status || error.name}`);
      });
    });
  }, [user?.uid]);

  useEffect(() => {
    const update = event => setBookmarkBarVisible(event.detail?.visible !== false);
    window.addEventListener('cachebrowse:bookmark-bar', update);
    return () => window.removeEventListener('cachebrowse:bookmark-bar', update);
  }, []);

  useEffect(() => {
    const bridge = window.cacheBrowse;
    if (!bridge) {
      let active = true;
      healthApi.get().then(() => { if (active) setBackendStatus({ online: true, starting: false }); })
        .catch(() => { if (active) setBackendStatus({ online: false, starting: false }); });
      return () => { active = false; };
    }
    let active = true;
    bridge.getBackendStatus().then(status => {
      if (active) setBackendStatus(status);
    }).catch(error => console.warn(`[Backend] Status unavailable: ${error.message}`));
    const unsubscribe = bridge.subscribe('backendStatus', status => setBackendStatus(status));
    return () => { active = false; unsubscribe(); };
  }, []);

  useEffect(() => {
    const bridge = window.cacheBrowse;
    if (!bridge) return undefined;
    const showWebPage = !authOpen && panel === 'browser' && Boolean(activeTab?.url) && !browser.error;
    bridge.setBrowserVisible(showWebPage);
    return undefined;
  }, [authOpen, panel, activeTab?.id, activeTab?.url, browser.error]);

  useEffect(() => {
    const bridge = window.cacheBrowse;
    if (!bridge) return undefined;
    return bridge.subscribe('focusAddress', () => {
      document.querySelector('.address-bar__input')?.focus();
    });
  }, []);

  const handleNavigate = useCallback(async input => {
    setPanel('browser');
    setStatusText('Loading');
    const ok = await browser.navigate(input);
    if (ok) {
      setStatusText('Ready');
      refreshStats();
    } else setStatusText('Unable to load page');
  }, [browser.navigate, refreshStats]);

  const handleBookmark = useCallback(() => {
    if (!user?.uid) { setAuthIntent('bookmarks'); setAuthOpen(true); return; }
    if (browser.url) setBookmarkRequest(value => value + 1);
  }, [browser.url, user?.uid]);

  const handleSaveBookmark = useCallback(async bookmark => {
    try {
      const normalizedUrl = new URL(bookmark.url).href;
      const duplicate = bookmarks.find(item => item.url === normalizedUrl && item.id !== bookmark.id);
      if (duplicate) throw new Error('That URL is already bookmarked.');
      let outcome;
      if (bookmark.id) {
        outcome = await bookmarkApi.update(bookmark.id, normalizedUrl, bookmark.title.trim());
      } else {
        outcome = await bookmarkApi.add(normalizedUrl, bookmark.title.trim() || normalizedUrl);
      }
      const latest = await refreshBookmarks();
      setStatusText(outcome?.persistenceAvailable
        ? (bookmark.id ? 'Bookmark updated' : 'Bookmark saved')
        : 'Bookmark is available for this session only; D1 persistence is unavailable');
      return latest?.find(item => item.url === normalizedUrl) || (bookmark.id ? { ...bookmark, url: normalizedUrl } : null);
    } catch (error) {
      setStatusText(`Bookmark failed: ${error.message}`);
      return null;
    }
  }, [bookmarks, refreshBookmarks]);

  const handleRemoveBookmark = useCallback(async item => {
    try {
      await bookmarkApi.remove(item.id);
      setBookmarks(items => items.filter(bookmark => bookmark.id !== item.id));
      setStatusText('Bookmark removed');
      return true;
    } catch (error) {
      setStatusText(`Bookmark removal failed: ${error.message}`);
      return false;
    }
  }, []);

  const handleHome = useCallback(() => {
    setPanel('browser');
    browser.home();
  }, [browser.home]);
  const togglePanel = useCallback(name => {
    if (['history', 'bookmarks', 'settings', 'dashboard', 'admin'].includes(name) && !user?.uid) {
      setAuthIntent(name); setAuthOpen(true); return;
    }
    if (name === 'admin' && !user?.admin) { setStatusText('Administrator access is not enabled for this account'); return; }
    setPanel(current => current === name ? 'browser' : name);
  }, [user?.uid, user?.admin]);

  const handleProtectionToggle = useCallback(async kind => {
    try {
      if (kind === 'ad') await toggleAdBlock();
      else await toggleTrackerBlock();
      await window.cacheBrowse?.refreshProtection();
      await refreshStats();
    } catch (error) {
      setStatusText(`Protection setting failed: ${error.message}`);
    }
  }, [toggleAdBlock, toggleTrackerBlock, refreshStats]);

  let content;
  if (panel === 'admin') content = <AdminDashboardPage />;
  else if (panel === 'dashboard') content = <DashboardPage />;
  else if (panel === 'history') content = <HistoryPage onNavigate={handleNavigate} />;
  else if (panel === 'bookmarks') content = <BookmarksPage onNavigate={handleNavigate} onChanged={refreshBookmarks} />;
  else if (panel === 'downloads') content = <DownloadsPage />;
  else if (panel === 'settings') content = <SettingsPage />;
  else if (browser.error) content = <BrowserViewport
    error={browser.error}
    onRetry={() => handleNavigate(browser.lastInput || browser.url)}
    onBack={browser.goBack}
  />;
  else if (browser.url) content = <BrowserViewport url={browser.url} />;
  else content = <HomePage onNavigate={handleNavigate} stats={stats} protection={protection} bookmarks={bookmarks} onOpenSettings={() => togglePanel('settings')} />;

  const visibleStatus = statsError
    ? `${statusText} · statistics unavailable`
    : backendStatus.starting
      ? 'Starting Java services'
      : !backendStatus.online
        ? window.cacheBrowse ? 'Java services unavailable' : 'Java services unavailable · web shell remains available'
        : statusText;

  return (
    <div className="cb-app">
      <BrowserToolbar
        browserEngineAvailable={Boolean(window.cacheBrowse)}
        url={browser.url}
        loading={browser.loading}
        canGoBack={browser.canGoBack}
        canGoForward={browser.canGoForward}
        onNavigate={handleNavigate}
        onBack={browser.goBack}
        onForward={browser.goForward}
        onReload={browser.reload}
        onStop={browser.stop}
        onHome={handleHome}
        onHistory={() => togglePanel('history')}
        onBookmark={handleBookmark}
        onBookmarks={() => togglePanel('bookmarks')}
        onDownloads={() => togglePanel('downloads')}
        onDashboard={() => togglePanel('dashboard')}
        onAdmin={() => togglePanel('admin')}
        onSettings={() => togglePanel('settings')}
        onLogin={() => { setAuthIntent(null); setAuthOpen(true); }}
        bookmarkActive={Boolean(bookmarks.find(item => item.url === browser.url))}
        protection={protection}
        onToggleAdBlock={() => handleProtectionToggle('ad')}
        onToggleTrackerBlock={() => handleProtectionToggle('tracker')}
        stats={stats}
        user={user}
        onLogout={logout}
      />
      <TabBar
        tabs={tabs}
        activeTab={activeTabId}
        onSwitch={switchTab}
        onClose={closeTab}
        onNew={addTab}
        onDuplicate={duplicateTab}
      />
      <BookmarksBar
        bookmarks={bookmarks}
        onOpen={handleNavigate}
        onOpenNewTab={url => window.cacheBrowse?.newTab(url)}
        onSaveBookmark={handleSaveBookmark}
        onRemove={handleRemoveBookmark}
        onShowAll={() => togglePanel('bookmarks')}
        visible={bookmarkBarVisible}
        onToggleVisible={() => setBookmarkBarVisible(value => {
          const next = !value;
          try { localStorage.setItem('cachebrowse.bookmark-bar', next ? 'visible' : 'hidden'); } catch {}
          window.dispatchEvent(new CustomEvent('cachebrowse:bookmark-bar', { detail: { visible: next } }));
          return next;
        })}
        currentPage={{ url: browser.url, title: activeTab?.title && activeTab.title !== 'New Tab' ? activeTab.title : browser.url }}
        bookmarkRequest={bookmarkRequest}
      />
      <main className="cb-content">{content}</main>
      <StatusBar stats={stats} statusText={browser.loading ? 'Loading' : visibleStatus} />
      {authOpen && <div className="auth-overlay" role="dialog" aria-modal="true" aria-label="Sign in to CacheBrowse">
        <button className="auth-overlay__close" aria-label="Close sign in" onClick={() => setAuthOpen(false)}>×</button>
        <LoginPage configurationError={firebaseConfigError} onLogin={async (...args) => { await login(...args); setAuthOpen(false); if (authIntent) setPanel(authIntent); }} onRegister={async (...args) => { await register(...args); setAuthOpen(false); if (authIntent) setPanel(authIntent); }} onGoogle={async () => { await loginGoogle(); setAuthOpen(false); if (authIntent) setPanel(authIntent); }} onForgotPassword={resetPassword} />
      </div>}
    </div>
  );
}
