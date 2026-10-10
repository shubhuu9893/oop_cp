import React, { useEffect, useState } from 'react';
import { statsApi, cacheApi } from '../services/api.js';
import { IconDashboard, IconRefresh, IconTrash } from '../components/icons/Icons.jsx';
import './DashboardPage.css';

export default function DashboardPage() {
  const [stats, setStats] = useState(null);
  const [cache, setCache] = useState(null);
  const [browserCacheSize, setBrowserCacheSize] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    setError('');
    try {
      const [nextStats, nextCache] = await Promise.all([statsApi.get(), cacheApi.getStatus()]);
      setStats(nextStats);
      setCache(nextCache);
      if (window.cacheBrowse) setBrowserCacheSize(await window.cacheBrowse.getBrowserCacheSize());
    } catch (reason) {
      setError(reason.message);
    } finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const clearCaches = async () => {
    setError('');
    try {
      if (window.cacheBrowse) await window.cacheBrowse.clearBrowserCache();
      await cacheApi.clear();
      await load();
    } catch (reason) { setError(`Could not clear caches: ${reason.message}`); }
  };

  if (loading) return <div className="page-center"><span className="spinner" /></div>;

  const cards = [
    { label: 'Ads blocked', value: stats?.adsBlocked ?? '—', color: 'cyan', icon: '↯' },
    { label: 'Trackers blocked', value: stats?.trackersBlocked ?? '—', color: 'purple', icon: '◉' },
    { label: 'Java app cache entries', value: cache ? `${cache.entryCount} / ${cache.capacity}` : '—', color: 'primary', icon: '◫' },
    { label: 'Java app cache lookups', value: stats ? stats.cacheHits + stats.cacheMisses : '—', color: 'muted', icon: '↻' },
  ];

  return (
    <div className="dashboard-page">
      <div className="dashboard-header">
        <div className="flex items-center gap-2">
          <IconDashboard size={18} style={{ color: 'var(--accent-primary)' }} />
          <h1>Cache &amp; Protection</h1>
        </div>
        <div className="flex gap-2">
          <button className="dash-btn secondary" onClick={load}><IconRefresh size={13} /> Refresh</button>
          <button className="dash-btn danger" onClick={clearCaches}><IconTrash size={13} /> Clear Caches</button>
        </div>
      </div>
      {error && <p role="alert" className="dashboard-error">{error}</p>}

      <div className="dashboard-grid">
        {cards.map(card => (
          <div key={card.label} className={`dash-card dash-card--${card.color}`}>
            <span className="dash-card__icon">{card.icon}</span>
            <span className="dash-card__value">{card.value}</span>
            <span className="dash-card__label">{card.label}</span>
          </div>
        ))}
      </div>

      <div className="dashboard-section">
        <h2 className="dashboard-section__title">Cache details</h2>
        <div className="dash-table">
          {[
            ['Application cache', 'Java LRU HTTP-response cache; separate from rendered page traffic'],
            ['Application cache hits', stats?.cacheHits ?? '—'],
            ['Application cache misses', stats?.cacheMisses ?? '—'],
            ['Application cache evictions', stats?.evictions ?? '—'],
            ['Expired application entries', stats?.expiredEntries ?? '—'],
            ['Host browser page cache', browserCacheSize == null ? (window.cacheBrowse ? 'Size unavailable' : 'Managed by your browser') : formatBytes(browserCacheSize)],
            ['Host browser cache cleanup', window.cacheBrowse ? 'Available from Clear Caches' : 'Use host browser settings'],
          ].map(([label, value]) => (
            <div key={label} className="dash-table__row">
              <span className="dash-table__key">{label}</span>
              <span className="dash-table__val">{value}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function formatBytes(value = 0) {
  if (!value) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  return `${(value / (1024 ** index)).toFixed(index ? 1 : 0)} ${units[index]}`;
}
