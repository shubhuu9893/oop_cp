import React, { useEffect, useState } from 'react';
import { IconSearch, IconShield, IconPlus, IconX, IconSettings } from '../components/icons/Icons.jsx';
import './HomePage.css';

const DEFAULT_SHORTCUTS = [
  { name: 'Google', url: 'https://www.google.com' },
  { name: 'DuckDuckGo', url: 'https://duckduckgo.com' },
  { name: 'YouTube', url: 'https://www.youtube.com' },
  { name: 'GitHub', url: 'https://github.com' },
  { name: 'Wikipedia', url: 'https://www.wikipedia.org' },
];
const STORAGE_KEY = 'cachebrowse.shortcuts.v1';

function readShortcuts() {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (Array.isArray(value)) return value.filter(item => item && typeof item.name === 'string' && typeof item.url === 'string').slice(0, 12);
  } catch (error) { console.warn('[Home] Stored shortcuts could not be read', error); }
  return DEFAULT_SHORTCUTS;
}

export default function HomePage({ onNavigate, stats, protection, bookmarks = [], onOpenSettings }) {
  const [query, setQuery] = useState('');
  const [shortcuts, setShortcuts] = useState(readShortcuts);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ name: '', url: '' });

  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(shortcuts)); }
    catch (error) { console.warn('[Home] Shortcuts could not be persisted', error); }
  }, [shortcuts]);

  const submit = event => {
    event.preventDefault();
    const text = query.trim();
    if (!text) return;
    onNavigate?.(text);
    setQuery('');
  };
  const saveShortcut = event => {
    event.preventDefault();
    const name = draft.name.trim();
    let url = draft.url.trim();
    if (!name || !url || shortcuts.length >= 12) return;
    if (!/^[a-z][a-z\d+.-]*:\/\//i.test(url)) url = `https://${url}`;
    try { url = new URL(url).href; } catch { return; }
    setShortcuts(current => [...current, { name, url }]);
    setDraft({ name: '', url: '' });
    setEditing(false);
  };
  const allShortcuts = [...shortcuts, ...bookmarks.filter(bookmark => !shortcuts.some(item => item.url === bookmark.url)).slice(0, Math.max(0, 12 - shortcuts.length)).map(item => ({ name: item.title || item.url, url: item.url, bookmark: true }))];

  return (
    <section className="home-page">
      <div className="home-content">
        <div className="home-brand">
          <div className="home-brand__mark" aria-hidden="true"><IconShield size={24} /></div>
          <div><div className="home-brand__title">CacheBrowse</div><div className="home-brand__sub">A quieter place to browse</div></div>
        </div>

        <form className="home-search" onSubmit={submit} role="search">
          <IconSearch size={18} aria-hidden="true" />
          <input aria-label="Search or enter a website address" type="text" placeholder="Search or enter address" value={query} onChange={event => setQuery(event.target.value)} />
          {query && <button type="button" className="home-search__clear" aria-label="Clear search" onClick={() => setQuery('')}><IconX size={15} /></button>}
          <button type="submit" className="home-search__submit" aria-label="Search"><IconSearch size={16} /></button>
        </form>

        <div className="home-section">
          <div className="home-section__heading"><h2>Shortcuts</h2><button className="home-text-button" onClick={() => setEditing(value => !value)} aria-label={editing ? 'Finish editing shortcuts' : 'Edit shortcuts'}><IconSettings size={14} /> {editing ? 'Done' : 'Edit'}</button></div>
          <div className="home-shortcuts">
            {allShortcuts.map((item, index) => <div className="shortcut-wrap" key={`${item.url}-${index}`}>
              <button className="shortcut-tile" onClick={() => editing ? setShortcuts(current => current.filter(entry => entry.url !== item.url)) : onNavigate?.(item.url)} title={editing ? `Remove ${item.name}` : item.url} aria-label={editing ? `Remove ${item.name}` : `Open ${item.name}`}>
                <span className="shortcut-tile__icon">{item.bookmark ? '★' : <ShortcutMark url={item.url} />}</span><span className="shortcut-tile__name">{item.name}</span>
              </button>
            </div>)}
            {editing && shortcuts.length < 12 && <button className="shortcut-tile shortcut-tile--add" onClick={() => setEditing('add')} aria-label="Add shortcut"><span className="shortcut-tile__icon"><IconPlus size={19} /></span><span className="shortcut-tile__name">Add site</span></button>}
          </div>
          {editing === 'add' && <form className="shortcut-form" onSubmit={saveShortcut}><input aria-label="Shortcut name" placeholder="Name" value={draft.name} onChange={event => setDraft({ ...draft, name: event.target.value })} autoFocus /><input aria-label="Shortcut address" placeholder="Website address" value={draft.url} onChange={event => setDraft({ ...draft, url: event.target.value })} /><button type="submit">Save</button><button type="button" onClick={() => setEditing(true)}>Cancel</button></form>}
        </div>

        <div className="home-footer">
          <div className="home-protection"><span className={`protection-dot${protection?.runtime?.adBlockEnforcing === true ? '' : ' is-off'}`} />{protection?.runtime?.adBlockEnforcing === true ? 'Protection active' : 'Protection status unavailable'}</div>
          <div className="home-stats" aria-label="Browser protection statistics">
            <span><b>{stats?.adsBlocked ?? '—'}</b> ads blocked</span><span><b>{stats?.trackersBlocked ?? '—'}</b> trackers blocked</span>
          </div>
          {onOpenSettings && <button className="home-text-button" onClick={onOpenSettings}>Customize</button>}
        </div>
      </div>
    </section>
  );
}

function ShortcutMark({ url }) {
  try { const host = new URL(url).hostname.replace(/^www\./, ''); return <span>{host.slice(0, 1).toUpperCase()}</span>; } catch { return <span>↗</span>; }
}
