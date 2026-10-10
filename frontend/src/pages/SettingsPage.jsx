import React, { useState, useEffect } from 'react';
import { settingsApi, protectionApi, cacheApi, healthApi } from '../services/api.js';
import { useTheme } from '../hooks/useTheme.js';
import { IconSettings, IconSun, IconMoon, IconCheck } from '../components/icons/Icons.jsx';
import './SettingsPage.css';

export default function SettingsPage() {
  const { theme, toggle, isDark } = useTheme();
  const [settings, setSettings]   = useState(null);
  const [saved, setSaved]         = useState(false);
  const [exceptions, setExceptions] = useState([]);
  const [domain, setDomain] = useState('');
  const [message, setMessage] = useState('');
  const [health, setHealth] = useState(null);
  const [checkingDatabase, setCheckingDatabase] = useState(false);
  const [browserCacheSize, setBrowserCacheSize] = useState(null);
  const [bookmarkBarVisible, setBookmarkBarVisible] = useState(() => {
    try { return localStorage.getItem('cachebrowse.bookmark-bar') !== 'hidden'; } catch { return true; }
  });

  useEffect(() => {
    settingsApi.get().then(setSettings).catch(() => {});
    protectionApi.getExceptions().then(setExceptions).catch(() => {});
    healthApi.get().then(setHealth).catch(() => {});
    window.cacheBrowse?.getBrowserCacheSize().then(setBrowserCacheSize).catch(() => {});
  }, []);

  const update = (key, value) =>
    setSettings(prev => prev ? { ...prev, [key]: value } : prev);

  const save = async () => {
    if (!settings) return;
    try {
      const result = await settingsApi.update(settings);
      await window.cacheBrowse?.refreshProtection();
      setSaved(true);
      setMessage(result?.persistenceAvailable
        ? 'Settings saved to Cloudflare D1'
        : 'Settings applied for this session; Cloudflare D1 persistence is unavailable');
      setTimeout(() => setSaved(false), 2000);
    } catch (error) {
      setMessage(`Could not save settings: ${error.message}`);
    }
  };

  const addAllowlist = async () => {
    try {
      await protectionApi.addAllowlist(domain);
      setExceptions(await protectionApi.getExceptions());
      setDomain('');
      await window.cacheBrowse?.refreshProtection();
      setMessage('Site added to allowlist');
    } catch (error) { setMessage(`Could not add site: ${error.message}`); }
  };

  const removeAllowlist = async value => {
    try {
      await protectionApi.removeAllowlist(value);
      setExceptions(await protectionApi.getExceptions());
      await window.cacheBrowse?.refreshProtection();
    } catch (error) { setMessage(`Could not remove site: ${error.message}`); }
  };

  const clearBrowserCache = async () => {
    try {
      if (window.cacheBrowse) await window.cacheBrowse.clearBrowserCache();
      await cacheApi.clear();
      if (window.cacheBrowse) {
        setBrowserCacheSize(await window.cacheBrowse.getBrowserCacheSize());
        setMessage('Chromium and Java application-managed caches cleared');
      } else {
        setMessage('Java application-managed cache cleared. The host browser cache is controlled by that browser.');
      }
    } catch (error) { setMessage(`Could not clear cache: ${error.message}`); }
  };

  const checkDatabase = async () => {
    setCheckingDatabase(true);
    try {
      const result = await healthApi.checkDatabase();
      setHealth(await healthApi.get());
      setMessage(result.schemaInitialized ? 'Cloudflare D1 connection and schema verified.' : 'D1 schema is not initialized.');
    } catch (error) {
      try { setHealth(await healthApi.get()); } catch { /* Keep last known diagnostics. */ }
      let detail = health?.persistenceIssue || error.message;
      try {
        const payload = JSON.parse(error.message);
        detail = payload.issue || payload.error || detail;
      } catch { /* Error message was not a JSON response. */ }
      setMessage(`D1 check failed (${detail}). Check backend diagnostics and restart after changing .env.`);
    } finally { setCheckingDatabase(false); }
  };

  return (
    <div className="settings-page">
      <div className="settings-header">
        <IconSettings size={16} style={{ color: 'var(--text-muted)' }} />
        <h1>Settings</h1>
      </div>

      <div className="settings-body">
        {health && (
          <div className={`settings-storage-status${health.persistenceAvailable ? ' ready' : ''}`}>
            <p>{health.persistenceAvailable
              ? 'Persistent storage: Cloudflare D1 connection and schema are verified.'
              : `Cloudflare D1 unavailable (${health.persistenceIssue || 'unknown'}). History, bookmarks, and settings use temporary in-memory storage.`}</p>
            <button className="dash-btn secondary" onClick={checkDatabase} disabled={checkingDatabase || !health.persistenceConfigured}>
              {checkingDatabase ? 'Checking D1…' : 'Check D1 connection'}
            </button>
          </div>
        )}
        {/* Appearance */}
        <Section title="Appearance">
          <Row label="Theme">
            <div className="theme-toggle">
              <button className={`theme-btn${!isDark ? ' active' : ''}`} onClick={() => !isDark || toggle()}>
                <IconSun size={13} /> Light
              </button>
              <button className={`theme-btn${isDark ? ' active' : ''}`} onClick={() => isDark || toggle()}>
                <IconMoon size={13} /> Dark
              </button>
            </div>
          </Row>
          <Row label="Bookmarks bar">
            <Toggle value={bookmarkBarVisible} onChange={visible => {
              setBookmarkBarVisible(visible);
              try { localStorage.setItem('cachebrowse.bookmark-bar', visible ? 'visible' : 'hidden'); } catch {}
              window.dispatchEvent(new CustomEvent('cachebrowse:bookmark-bar', { detail: { visible } }));
            }} />
          </Row>
        </Section>

        {/* Privacy */}
        <Section title="Privacy & Security">
          <Row label="Ad Blocking">
            <Toggle value={settings?.adBlockingEnabled ?? true} onChange={v => update('adBlockingEnabled', v)} />
          </Row>
          <Row label="Tracker Blocking">
            <Toggle value={settings?.trackerBlockingEnabled ?? true} onChange={v => update('trackerBlockingEnabled', v)} />
          </Row>
        </Section>

        <Section title="Protection exceptions">
          <Row label="Allow a site">
            <div className="settings-inline">
              <input className="settings-input" value={domain} onChange={e => setDomain(e.target.value)} placeholder="example.com" />
              <button className="dash-btn secondary" onClick={addAllowlist} disabled={!domain.trim()}>Add</button>
            </div>
          </Row>
          {exceptions.map(item => (
            <Row key={item} label={item}><button className="dash-btn danger" onClick={() => removeAllowlist(item)}>Remove</button></Row>
          ))}
          {!exceptions.length && <small>No site exceptions configured.</small>}
          <small>Filter rules are maintained internally and are not shown in ordinary settings.</small>
        </Section>

        {/* Browser */}
        <Section title="Browser">
          <Row label="Home Page">
            <input className="settings-input" value={settings?.homePage ?? ''} onChange={e => update('homePage', e.target.value)} placeholder="cachebrowse://home" />
          </Row>
          <Row label="Search Engine">
            <input className="settings-input" value={settings?.defaultSearchEngine ?? ''} onChange={e => update('defaultSearchEngine', e.target.value)} placeholder="https://www.google.com/search?q=%s" />
          </Row>
        </Section>

        {/* Cache */}
        <Section title="Cache">
          <Row label="Java service cache">
            <Toggle value={settings?.cacheEnabled ?? true} onChange={v => update('cacheEnabled', v)} />
          </Row>
          <small>Controls the Java-managed response cache. Your browser separately manages webpage caching.</small>
          <Row label="Cache Capacity">
            <input className="settings-input narrow" type="number" min="10" max="1000" value={settings?.cacheCapacity ?? 100} onChange={e => update('cacheCapacity', parseInt(e.target.value) || 100)} />
          </Row>
          <Row label="TTL (seconds)">
            <input className="settings-input narrow" type="number" min="10" value={Math.round((settings?.cacheTtl ?? 300000) / 1000)} onChange={e => update('cacheTtl', (parseInt(e.target.value) || 300) * 1000)} />
          </Row>
          <Row label="Host browser cache">
            <div className="settings-inline">
              <span>{browserCacheSize == null ? (window.cacheBrowse ? 'Size unavailable' : 'Managed by your browser') : formatBytes(browserCacheSize)}</span>
              <button className="dash-btn secondary" onClick={clearBrowserCache} disabled={!window.cacheBrowse}>Clear browser cache</button>
            </div>
          </Row>
        </Section>

        <button className="settings-save" onClick={save}>
          {saved ? <><IconCheck size={14} /> Saved!</> : 'Save Settings'}
        </button>
        {message && <p className="settings-message" role="status">{message}</p>}
      </div>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div className="settings-section">
      <h2 className="settings-section__title">{title}</h2>
      <div className="settings-section__body">{children}</div>
    </div>
  );
}

function Row({ label, children }) {
  return (
    <div className="settings-row">
      <span className="settings-row__label">{label}</span>
      <div className="settings-row__control">{children}</div>
    </div>
  );
}

function Toggle({ value, onChange }) {
  return (
    <button className={`settings-toggle ${value ? 'on' : 'off'}`} onClick={() => onChange(!value)}>
      <span className="settings-toggle__thumb" />
    </button>
  );
}

function formatBytes(value = 0) {
  if (!value) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  return `${(value / (1024 ** index)).toFixed(index ? 1 : 0)} ${units[index]}`;
}
