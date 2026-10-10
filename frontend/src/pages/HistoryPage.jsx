import React, { useEffect, useState } from 'react';
import { historyApi } from '../services/api.js';
import { IconHistory, IconTrash, IconExternalLink, IconRefresh } from '../components/icons/Icons.jsx';
import './ListPage.css';

export default function HistoryPage({ onNavigate }) {
  const [items, setItems]   = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try { setItems(await historyApi.getAll()); }
    catch (reason) { setError(`History could not be loaded: ${reason.message}`); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const del = async (id) => {
    try {
      await historyApi.delete(id);
      setItems(prev => prev.filter(i => i.id !== id));
      setError('');
    } catch (reason) { setError(`History entry could not be deleted: ${reason.message}`); }
  };

  const clear = async () => {
    try {
      await historyApi.clear();
      setItems([]);
      setError('');
    } catch (reason) { setError(`History could not be cleared: ${reason.message}`); }
  };

  return (
    <div className="list-page">
      <div className="list-header">
        <div className="flex items-center gap-2">
          <IconHistory size={16} style={{ color: 'var(--accent-primary)' }} />
          <h1>Browsing History</h1>
        </div>
        <div className="flex gap-2">
          <button className="dash-btn secondary" onClick={load}><IconRefresh size={13} /></button>
          <button className="dash-btn danger" onClick={clear}><IconTrash size={13} /> Clear All</button>
        </div>
      </div>
      {error && <p role="alert" className="dashboard-error">{error}</p>}
      <input className="list-search" aria-label="Search history" placeholder="Search history" value={query} onChange={event => setQuery(event.target.value)} />

      {loading ? <div className="page-center"><span className="spinner" /></div> :
       items.length === 0 ? <EmptyState icon={<IconHistory size={32} />} text="No history yet" /> : (
        <div className="list-body">
          {items.filter(item => `${item.title || ''} ${item.url}`.toLowerCase().includes(query.toLowerCase())).map(item => (
            <div key={item.id} className="list-item">
              <div className="list-item__info">
                <span className="list-item__title">{item.title || item.url}</span>
                <span className="list-item__url">{item.url}</span>
                {item.visitedAt && <span className="list-item__meta">{formatDate(item.visitedAt)}</span>}
              </div>
              <div className="list-item__actions">
                <button className="list-action" title="Open" onClick={() => onNavigate?.(item.url)}>
                  <IconExternalLink size={14} />
                </button>
                <button className="list-action danger" title="Delete" onClick={() => del(item.id)}>
                  <IconTrash size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function EmptyState({ icon, text }) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon" style={{ color: 'var(--text-muted)' }}>{icon}</span>
      <p>{text}</p>
    </div>
  );
}

function formatDate(d) {
  try { return new Date(d).toLocaleString(); } catch { return d; }
}
