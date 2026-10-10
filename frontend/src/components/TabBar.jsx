import React, { useEffect, useState } from 'react';
import { IconPlus, IconX, IconCopy } from './icons/Icons.jsx';
import './TabBar.css';

export default function TabBar({ tabs, activeTab, onSwitch, onClose, onNew, onDuplicate }) {
  const [menu, setMenu] = useState(null);
  useEffect(() => {
    if (!menu) return undefined;
    const close = event => { if (event.type === 'keydown' && event.key !== 'Escape') return; setMenu(null); };
    window.addEventListener('pointerdown', close);
    window.addEventListener('keydown', close);
    return () => { window.removeEventListener('pointerdown', close); window.removeEventListener('keydown', close); };
  }, [menu]);
  const contextMenu = (event, tab) => { event.preventDefault(); event.stopPropagation(); setMenu({ x: event.clientX, y: event.clientY, tabId: tab.id }); };
  const closeOthers = () => { tabs.filter(tab => tab.id !== menu.tabId).forEach(tab => onClose(tab.id)); setMenu(null); };
  const closeRight = () => { const index = tabs.findIndex(tab => tab.id === menu.tabId); tabs.slice(index + 1).forEach(tab => onClose(tab.id)); setMenu(null); };
  return (
    <div className="tabbar">
      <div className="tabbar__tabs">
        {tabs.map(tab => (
          <div
            key={tab.id}
            className={`tabbar__tab${tab.id === activeTab ? ' active' : ''}`}
            onClick={() => onSwitch(tab.id)}
            onContextMenu={event => contextMenu(event, tab)}
            role="tab"
            aria-selected={tab.id === activeTab}
            tabIndex={tab.id === activeTab ? 0 : -1}
            onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') onSwitch(tab.id); }}
            title={tab.url || tab.title}
          >
            {tab.loading ? (
              <span className="spinner" style={{ width: 12, height: 12, flexShrink: 0 }} />
            ) : (
              <span className="tabbar__favicon">
                {tab.favicon
                  ? <img src={tab.favicon} width={12} height={12} alt="" />
                  : <span className="tabbar__favicon-dot" />}
              </span>
            )}
            <span className="tabbar__title">{tab.title || 'New Tab'}</span>
            <button
              className="tabbar__duplicate"
              onClick={e => { e.stopPropagation(); onDuplicate?.(tab.id); }}
              title="Duplicate tab"
              aria-label={`Duplicate ${tab.title || 'tab'}`}
            ><IconCopy size={10} /></button>
            <button
              className="tabbar__close"
              onClick={e => { e.stopPropagation(); onClose(tab.id); }}
              title="Close tab"
              aria-label={`Close ${tab.title || 'tab'}`}
            >
              <IconX size={10} />
            </button>
          </div>
        ))}
      </div>
      <button className="tabbar__new" onClick={onNew} title="New tab" aria-label="New tab">
        <IconPlus size={14} />
      </button>
      {menu && <div className="tab-context-menu" style={{ left: menu.x, top: menu.y }} role="menu" onPointerDown={event => event.stopPropagation()}>
        <button role="menuitem" onClick={() => { onDuplicate?.(menu.tabId); setMenu(null); }}>Duplicate tab</button>
        <button role="menuitem" onClick={() => { onClose(menu.tabId); setMenu(null); }}>Close tab</button>
        <button role="menuitem" disabled={tabs.length < 2} onClick={closeOthers}>Close other tabs</button>
        <button role="menuitem" disabled={tabs.findIndex(tab => tab.id === menu.tabId) === tabs.length - 1} onClick={closeRight}>Close tabs to the right</button>
      </div>}
    </div>
  );
}
