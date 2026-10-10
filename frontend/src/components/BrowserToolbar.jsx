import React, { useState, useRef, useEffect } from 'react';
import AddressBar from './AddressBar.jsx';
import UserMenu from './UserMenu.jsx';
import ProtectionMenu from './ProtectionMenu.jsx';
import {
  IconArrowLeft, IconArrowRight, IconRefresh, IconStop, IconHome, IconDownload,
  IconHistory, IconBookmark, IconBookmarks,
  IconDashboard, IconSettings,
} from './icons/Icons.jsx';
import './BrowserToolbar.css';

function NavBtn({ icon: Icon, onClick, title, disabled }) {
  return (
    <button className={`toolbar-btn nav${disabled ? ' disabled' : ''}`} onClick={onClick} title={title} aria-label={title} disabled={disabled}>
      <Icon size={16} />
    </button>
  );
}

function ToolBtn({ icon: Icon, onClick, title, active }) {
  return (
    <button className={`toolbar-btn${active ? ' active' : ''}`} onClick={onClick} title={title} aria-label={title} aria-pressed={active}>
      <Icon size={15} />
    </button>
  );
}

export default function BrowserToolbar({
  url, loading,
  browserEngineAvailable = Boolean(window.cacheBrowse),
  canGoBack, canGoForward,
  onNavigate, onBack, onForward, onReload, onStop, onHome,
  onHistory, onBookmark, onBookmarks, onDownloads, onDashboard, onAdmin, onSettings,
  protection, onToggleAdBlock, onToggleTrackerBlock,
  user, onLogin, onLogout,
  stats,
  bookmarkActive = false,
}) {
  return (
    <div className="browser-toolbar">
      {/* Navigation */}
      <div className="toolbar-nav">
        <NavBtn icon={IconArrowLeft}  onClick={onBack}    title="Back"    disabled={!canGoBack} />
        <NavBtn icon={IconArrowRight} onClick={onForward} title="Forward" disabled={!canGoForward} />
        <NavBtn icon={browserEngineAvailable && loading ? IconStop : IconRefresh} onClick={loading ? onStop : onReload}
          title={browserEngineAvailable ? (loading ? 'Stop loading' : 'Reload') : 'In-app Chromium is unavailable; launch CacheBrowse with npm run dev'}
          disabled={!browserEngineAvailable} />
        <NavBtn icon={IconHome}       onClick={onHome}    title="Home"               />
      </div>

      {/* Address bar */}
      <div className="toolbar-address">
        <AddressBar url={url} loading={loading} onNavigate={onNavigate} />
      </div>

      {/* Actions */}
      <div className="toolbar-actions">
        <ToolBtn icon={IconHistory}   onClick={onHistory}   title="History"   />
        <ToolBtn icon={IconBookmark}  onClick={onBookmark}  title={bookmarkActive ? 'Remove bookmark' : 'Bookmark this page'} active={bookmarkActive} />
        <ToolBtn icon={IconBookmarks} onClick={onBookmarks} title="Bookmarks" />
        <ToolBtn icon={IconDownload} onClick={onDownloads} title="Downloads" />

        {/* Protection */}
        <ProtectionMenu
          protection={protection}
          stats={stats}
          onToggleAdBlock={onToggleAdBlock}
          onToggleTrackerBlock={onToggleTrackerBlock}
        />

        <div className="toolbar-divider" />

        <ToolBtn icon={IconDashboard} onClick={onDashboard} title="Dashboard" />
        <ToolBtn icon={IconSettings}  onClick={onSettings}  title="Settings"  />

        <UserMenu user={user} onLogin={onLogin} onLogout={onLogout} onDashboard={onDashboard} onAdmin={onAdmin} onSettings={onSettings} />
      </div>
    </div>
  );
}
