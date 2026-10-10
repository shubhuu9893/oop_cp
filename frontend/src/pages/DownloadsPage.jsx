import React, { useEffect, useState } from 'react';
import { IconDownload, IconExternalLink, IconX } from '../components/icons/Icons.jsx';
import './ListPage.css';

export default function DownloadsPage() {
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    const bridge = window.cacheBrowse;
    if (!bridge) return undefined;
    bridge.getDownloads().then(setItems).catch(reason => setError(`Downloads could not be loaded: ${reason.message}`));
    return bridge.subscribe('downloadChanged', item => {
      setItems(previous => [item, ...previous.filter(entry => entry.id !== item.id)]);
    });
  }, []);

  return (
    <div className="list-page">
      <div className="list-header">
        <div className="flex items-center gap-2">
          <IconDownload size={16} style={{ color: 'var(--accent-primary)' }} />
          <h1>Downloads</h1>
        </div>
      </div>
      {error && <p role="alert" className="dashboard-error">{error}</p>}
      {!window.cacheBrowse && <p className="dashboard-error">Downloads are managed by your host browser because CacheBrowse is running as a web app.</p>}
      {items.length === 0 ? (
        <div className="empty-state"><IconDownload size={30} /><p>No downloads yet</p></div>
      ) : (
        <div className="list-body">
          {items.map(item => (
            <div key={item.id} className="list-item">
              <div className="list-item__info">
                <span className="list-item__title">{item.filename}</span>
                <span className="list-item__url">{item.state} · {formatBytes(item.receivedBytes)} / {formatBytes(item.totalBytes)}</span>
              </div>
              <div className="list-item__actions">
                {item.state === 'progressing' && <button className="list-action" aria-label={`Cancel ${item.filename}`} title="Cancel download" onClick={() => window.cacheBrowse.cancelDownload(item.id)}><IconX size={14} /></button>}
                <button className="list-action" disabled={item.state !== 'completed'} title="Open file" onClick={() => window.cacheBrowse.openDownload(item.id)}>
                  <IconExternalLink size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function formatBytes(value = 0) {
  if (!value) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  return `${(value / (1024 ** index)).toFixed(index ? 1 : 0)} ${units[index]}`;
}
