import React, { useEffect, useRef, useState } from 'react';
import { IconBookmarks, IconDashboard, IconGlobe, IconLogout, IconSearch, IconSettings, IconShield, IconX } from './components/icons/Icons.jsx';
import './styles/variables.css';
import './components/UserMenu.css';
import './components/ProtectionMenu.css';
import './components/BookmarksBar.css';
import './styles/overlay.css';

export default function OverlayApp() {
  const [popup, setPopup] = useState(null);
  const panelRef = useRef(null);

  useEffect(() => window.cacheBrowseOverlay?.subscribe(setPopup), []);
  useEffect(() => {
    if (!popup) return undefined;
    const focus = requestAnimationFrame(() => panelRef.current?.querySelector('input, button')?.focus());
    const keydown = event => {
        if (event.key === 'Escape') {
        event.stopPropagation();
        window.cacheBrowseOverlay?.close('escape');
      }
    };
    window.addEventListener('keydown', keydown);
    return () => { cancelAnimationFrame(focus); window.removeEventListener('keydown', keydown); };
  }, [popup?.id]);

  if (!popup) return null;
  const { x, y, width, height } = popup.bounds;
  const act = action => window.cacheBrowseOverlay?.action(popup.id, action);
  return (
    <div className="native-popup-overlay" onPointerDown={event => {
      if (event.target === event.currentTarget) window.cacheBrowseOverlay?.close('outside');
    }}>
      <section
        ref={panelRef}
        className={`native-popup-overlay__panel ${popup.kind === 'profile' ? 'user-dropdown' : popup.kind === 'protection' ? 'protection-popup' : popup.kind === 'bookmarks' ? 'bookmarks-popover' : 'bookmark-context-menu'}`}
        role={popup.kind === 'profile' ? 'menu' : 'dialog'}
        aria-label={popup.kind === 'profile' ? 'Profile menu' : 'CacheBrowse Protection'}
        style={{ left: 0, top: 0, width, maxHeight: height }}
      >
        {popup.kind === 'profile' && <ProfilePopup data={popup.payload} act={act} />}
        {popup.kind === 'protection' && <ProtectionPopup data={popup.payload} act={act} />}
        {popup.kind === 'bookmarks' && <BookmarksPopup data={popup.payload} act={act} />}
        {popup.kind === 'bookmark-context' && <BookmarkContext data={popup.payload} act={act} />}
      </section>
    </div>
  );
}

function ProfilePopup({ data = {}, act }) {
  return <>
    <div className="user-dropdown__profile">
      <span className="user-avatar lg">{data.initial || '?'}</span>
      <div className="user-dropdown__info">
        <span className="user-dropdown__name">{data.displayName || data.email || 'Account'}</span>
        <span className="user-dropdown__email">{data.email || ''}</span>
      </div>
    </div>
    <div className="user-dropdown__divider" />
    <MenuAction icon={IconDashboard} label="Dashboard" onClick={() => act('dashboard')} />
    <MenuAction icon={IconSettings} label="Settings" onClick={() => act('settings')} />
    <div className="user-dropdown__divider" />
    <MenuAction icon={IconLogout} label="Sign Out" danger onClick={() => act('signout')} />
  </>;
}

function MenuAction({ icon: Icon, label, onClick, danger }) {
  return <button className={`user-dropdown__item${danger ? ' danger' : ''}`} role="menuitem" onClick={onClick}><Icon size={14} /><span>{label}</span></button>;
}

function ProtectionPopup({ data = {}, act }) {
  return <>
    <div className="protection-popup__header"><IconShield size={16} style={{ color: 'var(--accent-secondary)' }} /><span>CacheBrowse Protection</span></div>
    <ProtectionToggle label="Ad Blocking" enabled={data.adBlock} enforcing={data.adEnforcing} onClick={() => act('toggle-ad')} />
    <ProtectionToggle label="Tracker Blocking" enabled={data.trackerBlock} enforcing={data.trackerEnforcing} onClick={() => act('toggle-tracker')} />
    <div className="protection-popup__divider" />
    <div className="protection-runtime-status" role="status">{data.status || 'Protection status unavailable'}</div>
    <div className="protection-popup__stats">
      <StatRow label="Ads Blocked" value={data.adsBlocked} color="cyan" />
      <StatRow label="Trackers Blocked" value={data.trackersBlocked} color="purple" />
      <StatRow label="Requests Blocked" value={data.blockedRequests} color="danger" />
    </div>
  </>;
}

function ProtectionToggle({ label, enabled, enforcing, onClick }) {
  const status = enforcing ? 'ON' : enabled ? 'NOT ACTIVE' : 'OFF';
  return <div className="protection-popup__row">
    <div className="protection-popup__label"><span>{label}</span><span className={`badge ${enforcing ? 'badge-success' : 'badge-danger'}`}>{status}</span></div>
    <button aria-label={`Toggle ${label.toLowerCase()}`} aria-pressed={enabled === true} className={`protection-toggle ${enabled ? 'on' : 'off'}`} onClick={onClick}><span className="protection-toggle__thumb" /></button>
  </div>;
}

function StatRow({ label, value, color }) {
  return <div className="stat-row"><span className="stat-row__label">{label}</span><span className={`stat-row__value text-${color}`}>{value ?? '—'}</span></div>;
}

function BookmarksPopup({ data = {}, act }) {
  const items = Array.isArray(data.items) ? data.items : [];
  const folders = Array.isArray(data.folders) ? data.folders : [];
  const editing = data.editing;
  if (editing) return <BookmarkEditor item={editing} folders={folders} act={act} />;
  return <>
    <div className="bookmarks-popover__heading"><IconBookmarks size={15} /><strong>Bookmarks</strong><span>{items.length}</span></div>
    <label className="bookmarks-popover__search"><IconSearch size={14} /><input autoFocus placeholder="Search bookmarks" value={data.query || ''} onChange={event => act('query', event.target.value)} /></label>
    <div className="bookmarks-popover__folders" aria-label="Bookmark folders">
      <button className={data.folderFilter === 'all' ? 'selected' : ''} onClick={() => act('folder', 'all')}>All</button>
      <button className={data.folderFilter === 'unfiled' ? 'selected' : ''} onClick={() => act('folder', 'unfiled')}>Bookmarks bar</button>
      {folders.map(folder => <button key={folder} className={data.folderFilter === folder ? 'selected' : ''} onClick={() => act('folder', folder)}>{folder}</button>)}
    </div>
    <div className="bookmarks-popover__list">
      {items.map(item => <div className="bookmark-row" key={item.id}>
        <button className="bookmark-row__open" title={`${item.title} — ${item.url}`} onClick={() => act('open', item)}>
          <BookmarkFavicon url={item.url} /><span><b>{item.title || item.url}</b><small>{item.url}</small></span>
        </button>
        <button title="Edit bookmark" aria-label={`Edit ${item.title}`} onClick={() => act('edit', item)}>Edit</button>
        <button title="Remove bookmark" aria-label={`Remove ${item.title}`} onClick={() => act('remove', item)}>×</button>
      </div>)}
      {!items.length && <div className="bookmarks-popover__empty">No matching bookmarks</div>}
    </div>
    <form className="bookmarks-popover__new-folder" onSubmit={event => { event.preventDefault(); const value = new FormData(event.currentTarget).get('folder'); act('create-folder', value); event.currentTarget.reset(); }}>
      <input name="folder" aria-label="New folder name" placeholder="New folder name" />
      <button>Create folder</button>
    </form>
    <button className="bookmarks-popover__manage" onClick={() => act('manage')}>Open bookmarks manager</button>
  </>;
}

function BookmarkContext({ data = {}, act }) {
  const item = data.item || {};
  return <>
    <button onClick={() => act('open-new-tab', item)}>Open in new tab</button>
    <button onClick={() => act('edit', item)}>Edit bookmark</button>
    <button className="danger" onClick={() => act('remove', item)}>Remove bookmark</button>
  </>;
}

function BookmarkEditor({ item, folders, act }) {
  const update = (key, value) => act('edit-change', { ...item, [key]: value });
  return <form className="bookmark-editor-native" onSubmit={event => { event.preventDefault(); act('save', item); }}>
    <header><strong>{item.id ? 'Edit bookmark' : 'Add bookmark'}</strong><button type="button" aria-label="Close" onClick={() => act('cancel-edit')}><IconX size={15} /></button></header>
    <label>Title<input autoFocus required maxLength={160} value={item.title || ''} onChange={event => update('title', event.target.value)} /></label>
    <label>URL<input required type="url" value={item.url || ''} onChange={event => update('url', event.target.value)} /></label>
    <label>Folder<select value={item.folder || ''} onChange={event => update('folder', event.target.value)}><option value="">Bookmarks bar</option>{folders.map(folder => <option key={folder} value={folder}>{folder}</option>)}</select></label>
    <footer>{item.id && <button type="button" className="bookmark-editor__remove" onClick={() => act('remove', item)}>Remove</button>}<span /><button type="button" onClick={() => act('cancel-edit')}>Cancel</button><button className="bookmark-editor__save" type="submit">Save</button></footer>
  </form>;
}

function BookmarkFavicon({ url }) {
  const [failed, setFailed] = useState(false);
  let src = '';
  try { src = `${new URL(url).origin}/favicon.ico`; } catch { /* Fallback icon below. */ }
  return <span className="bookmark-favicon">{src && !failed ? <img src={src} alt="" onError={() => setFailed(true)} /> : <IconGlobe size={13} />}</span>;
}
