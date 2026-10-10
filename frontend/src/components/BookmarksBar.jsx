import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { IconBookmark, IconBookmarks, IconGlobe, IconSearch, IconX } from './icons/Icons.jsx';
import PopupLayer from './PopupLayer.jsx';
import './BookmarksBar.css';

const ORDER_KEY = 'cachebrowse.bookmark-order';
const FOLDERS_KEY = 'cachebrowse.bookmark-folders';
const MEMBERSHIP_KEY = 'cachebrowse.bookmark-folders-by-id';
const readJson = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } };

export default function BookmarksBar({ bookmarks = [], onOpen, onOpenNewTab, onSaveBookmark, onRemove, onShowAll, visible, onToggleVisible, currentPage, bookmarkRequest }) {
  const [order, setOrder] = useState(() => readJson(ORDER_KEY, []));
  const [folders, setFolders] = useState(() => readJson(FOLDERS_KEY, []));
  const [membership, setMembership] = useState(() => readJson(MEMBERSHIP_KEY, {}));
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [folderFilter, setFolderFilter] = useState('all');
  const [newFolder, setNewFolder] = useState('');
  const [editing, setEditing] = useState(null);
  const anchorRef = useRef(null);
  const close = useCallback(() => setOpen(false), []);

  useEffect(() => { try { localStorage.setItem(ORDER_KEY, JSON.stringify(order)); } catch {} }, [order]);
  useEffect(() => { try { localStorage.setItem(FOLDERS_KEY, JSON.stringify(folders)); } catch {} }, [folders]);
  useEffect(() => { try { localStorage.setItem(MEMBERSHIP_KEY, JSON.stringify(membership)); } catch {} }, [membership]);
  useEffect(() => {
    if (!bookmarkRequest || !currentPage?.url) return;
    const existing = bookmarks.find(item => item.url === currentPage.url);
    setEditing(existing
      ? { ...existing, folder: membership[existing.id] || '' }
      : { title: currentPage.title || currentPage.url, url: currentPage.url, folder: '' });
  }, [bookmarkRequest]);

  const ordered = useMemo(() => {
    const rank = new Map(order.map((id, index) => [id, index]));
    return [...bookmarks].sort((a, b) => (rank.get(a.id) ?? Number.MAX_SAFE_INTEGER) - (rank.get(b.id) ?? Number.MAX_SAFE_INTEGER));
  }, [bookmarks, order]);
  const barItems = ordered.filter(item => !membership[item.id]);
  const filtered = ordered.filter(item => {
    const inFolder = folderFilter === 'all' || (folderFilter === 'unfiled' ? !membership[item.id] : membership[item.id] === folderFilter);
    return inFolder && `${item.title} ${item.url}`.toLowerCase().includes(query.toLowerCase());
  });

  const move = (fromId, toId) => {
    const ids = ordered.map(item => item.id);
    const from = ids.indexOf(fromId); const to = ids.indexOf(toId);
    if (from < 0 || to < 0 || from === to) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    setOrder(ids);
  };
  const createFolder = event => {
    event.preventDefault();
    const name = newFolder.trim();
    if (!name || folders.some(folder => folder.toLowerCase() === name.toLowerCase())) return;
    setFolders(value => [...value, name]); setNewFolder('');
  };
  const edit = item => {
    setEditing({ ...item, folder: membership[item.id] || '' });
  };
  const remove = async item => {
    if (!await onRemove(item)) return;
    setOrder(value => value.filter(id => id !== item.id));
    setMembership(value => { const next = { ...value }; delete next[item.id]; return next; });
  };
  const saveEditor = async event => {
    event.preventDefault();
    const saved = await onSaveBookmark(editing);
    if (!saved) return;
    setMembership(value => {
      const next = { ...value };
      const targetId = saved.id || editing.id;
      if (editing.folder) next[targetId] = editing.folder; else delete next[targetId];
      if (editing.id && editing.id !== targetId) delete next[editing.id];
      return next;
    });
    setEditing(null);
  };

  if (!visible) return <div className="bookmark-bar-restore"><button onClick={onToggleVisible}>Show bookmarks bar</button></div>;
  return (
    <div className="bookmarks-bar" role="toolbar" aria-label="Bookmarks bar">
      <div className="bookmarks-bar__items">
        {barItems.map(item => <BookmarkButton key={item.id} item={item} onOpen={onOpen} onOpenNewTab={onOpenNewTab} onContextEdit={() => edit(item)} onContextRemove={() => remove(item)} onDrag={move} />)}
        {!barItems.length && <span className="bookmarks-bar__empty"><IconBookmark size={13} /> Your bookmarks appear here</span>}
      </div>
      <button ref={anchorRef} className="bookmarks-bar__all" title="All bookmarks" aria-label="All bookmarks" aria-expanded={open} onClick={() => setOpen(value => !value)}>
        <IconBookmarks size={14} /><span>All Bookmarks</span>
      </button>
      <button className="bookmarks-bar__hide" title="Hide bookmarks bar" aria-label="Hide bookmarks bar" onClick={onToggleVisible}><IconX size={13} /></button>

      <PopupLayer
        anchorRef={anchorRef} open={open} onClose={close} className="bookmarks-popover"
        overlayKind="bookmarks"
        overlayData={{ items: filtered.slice(0, 500), folders, query, folderFilter, editing }}
        onOverlayAction={async (action, value) => {
          if (action === 'query') setQuery(String(value || ''));
          else if (action === 'folder') setFolderFilter(String(value || 'all'));
          else if (action === 'open' && value?.url) { onOpen(value.url); close(); }
          else if (action === 'edit' && value) edit(value);
          else if (action === 'remove' && value) { await remove(value); if (value.id === editing?.id) setEditing(null); }
          else if (action === 'create-folder') {
            const name = String(value || '').trim();
            if (name && !folders.some(folder => folder.toLowerCase() === name.toLowerCase())) setFolders(current => [...current, name]);
          } else if (action === 'manage') { onShowAll(); close(); }
          else if (action === 'edit-change' && value) setEditing(value);
          else if (action === 'cancel-edit') setEditing(null);
          else if (action === 'save' && value) {
            const saved = await onSaveBookmark(value);
            if (!saved) return;
            setMembership(current => {
              const next = { ...current };
              const id = saved.id || value.id;
              if (value.folder) next[id] = value.folder; else delete next[id];
              if (value.id && value.id !== id) delete next[value.id];
              return next;
            });
            setEditing(null);
          }
        }}
      >
        <div className="bookmarks-popover__heading"><IconBookmarks size={15} /><strong>Bookmarks</strong><span>{bookmarks.length}</span></div>
        <label className="bookmarks-popover__search"><IconSearch size={14} /><input placeholder="Search bookmarks" value={query} onChange={event => setQuery(event.target.value)} /></label>
        <div className="bookmarks-popover__folders" aria-label="Bookmark folders">
          <button className={folderFilter === 'all' ? 'selected' : ''} onClick={() => setFolderFilter('all')}>All</button>
          <button className={folderFilter === 'unfiled' ? 'selected' : ''} onClick={() => setFolderFilter('unfiled')}>Bookmarks bar</button>
          {folders.map(folder => <button key={folder} className={folderFilter === folder ? 'selected' : ''} onClick={() => setFolderFilter(folder)}>{folder}</button>)}
        </div>
        <div className="bookmarks-popover__list">
          {filtered.map(item => <div className="bookmark-row" key={item.id}>
            <button className="bookmark-row__open" title={`${item.title} — ${item.url}`} onClick={() => { onOpen(item.url); close(); }}>
              <Favicon url={item.url} /><span><b>{item.title || item.url}</b><small>{item.url}</small></span>
            </button>
            <button title="Edit bookmark" aria-label={`Edit ${item.title}`} onClick={() => edit(item)}>Edit</button>
            <button title="Remove bookmark" aria-label={`Remove ${item.title}`} onClick={() => remove(item)}>×</button>
          </div>)}
          {!filtered.length && <div className="bookmarks-popover__empty">No matching bookmarks</div>}
        </div>
        <form className="bookmarks-popover__new-folder" onSubmit={createFolder}>
          <input aria-label="New folder name" placeholder="New folder name" value={newFolder} onChange={event => setNewFolder(event.target.value)} />
          <button disabled={!newFolder.trim()}>Create folder</button>
        </form>
        <button className="bookmarks-popover__manage" onClick={() => { onShowAll(); close(); }}>Open bookmarks manager</button>
      </PopupLayer>
      {editing && <div className="bookmark-editor-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setEditing(null); }}>
        <form className="bookmark-editor" role="dialog" aria-modal="true" aria-label="Edit bookmark" onSubmit={saveEditor}>
          <header><strong>{editing.id ? 'Edit bookmark' : 'Add bookmark'}</strong><button type="button" aria-label="Close" onClick={() => setEditing(null)}><IconX size={15} /></button></header>
          <label>Title<input required maxLength={160} value={editing.title || ''} onChange={event => setEditing(value => ({ ...value, title: event.target.value }))} /></label>
          <label>URL<input required type="url" value={editing.url || ''} onChange={event => setEditing(value => ({ ...value, url: event.target.value }))} /></label>
          <label>Folder<select value={editing.folder || ''} onChange={event => setEditing(value => ({ ...value, folder: event.target.value }))}><option value="">Bookmarks bar</option>{folders.map(folder => <option key={folder} value={folder}>{folder}</option>)}</select></label>
          <footer>{editing.id && <button type="button" className="bookmark-editor__remove" onClick={() => { remove(editing); setEditing(null); }}>Remove</button>}<span /><button type="button" onClick={() => setEditing(null)}>Cancel</button><button className="bookmark-editor__save" type="submit">Save</button></footer>
        </form>
      </div>}
    </div>
  );
}

function BookmarkButton({ item, onOpen, onOpenNewTab, onContextEdit, onContextRemove, onDrag }) {
  const [menu, setMenu] = useState(false);
  const ref = useRef(null);
  const close = useCallback(() => setMenu(false), []);
  return <>
    <button ref={ref} className="bookmark-chip" title={`${item.title || item.url}\n${item.url}`} onClick={() => onOpen(item.url)} onContextMenu={event => { event.preventDefault(); setMenu(true); }} draggable onDragStart={event => event.dataTransfer.setData('text/cachebrowse-bookmark', item.id)} onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); onDrag(event.dataTransfer.getData('text/cachebrowse-bookmark'), item.id); }}>
      <Favicon url={item.url} /><span>{item.title || item.url}</span>
    </button>
    <PopupLayer
      anchorRef={ref} open={menu} onClose={close} className="bookmark-context-menu" align="left"
      overlayKind="bookmark-context" overlayData={{ item }} closeAfterAction
      onOverlayAction={async (action, value) => {
        if (action === 'open-new-tab') onOpenNewTab?.(value?.url || item.url);
        else if (action === 'edit') onContextEdit();
        else if (action === 'remove') onContextRemove();
      }}
    >
      <button disabled={!onOpenNewTab} onClick={() => { onOpenNewTab?.(item.url); close(); }}>Open in new tab</button>
      <button onClick={() => { onContextEdit(); close(); }}>Edit bookmark</button>
      <button className="danger" onClick={() => { onContextRemove(); close(); }}>Remove bookmark</button>
    </PopupLayer>
  </>;
}

function Favicon({ url }) {
  const [failed, setFailed] = useState(false);
  let src = '';
  try { src = `${new URL(url).origin}/favicon.ico`; } catch {}
  return <span className="bookmark-favicon">{src && !failed ? <img src={src} alt="" onError={() => setFailed(true)} /> : <IconGlobe size={13} />}</span>;
}
