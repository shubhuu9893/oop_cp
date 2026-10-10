import React, { useState, useRef, useCallback } from 'react';
import { IconUser, IconDashboard, IconSettings, IconLogout, IconChevronDown } from './icons/Icons.jsx';
import PopupLayer from './PopupLayer.jsx';
import './UserMenu.css';

export default function UserMenu({ user, onLogin, onLogout, onDashboard, onAdmin, onSettings }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  const close = useCallback(() => setOpen(false), []);

  const displayName = user?.displayName || '';
  const initial = (displayName || user?.email || '?')[0].toUpperCase();
  const email   = user?.email ?? '';

  if (!user) {
    return (
      <button className="user-login-btn" onClick={onLogin} title="Sign in">
        <IconUser size={14} />
        <span>Sign In</span>
      </button>
    );
  }

  return (
    <div className="user-menu" ref={ref}>
      <button ref={ref} className="user-avatar-btn" onClick={() => setOpen(o => !o)} title={email} aria-label="Profile menu" aria-expanded={open}>
        <span className="user-avatar">{initial}</span>
        <IconChevronDown size={12} style={{ color: 'var(--text-muted)', marginLeft: 2 }} />
      </button>

      <PopupLayer
        anchorRef={ref} open={open} onClose={close} className="user-dropdown fade-in"
        overlayKind="profile" overlayData={{ displayName: displayName || email.split('@')[0], email, initial, admin: user.admin === true }} closeAfterAction
        onOverlayAction={action => {
          if (action === 'dashboard') onDashboard?.();
          else if (action === 'admin') onAdmin?.();
          else if (action === 'settings') onSettings?.();
          else if (action === 'signout') onLogout?.();
        }}
      >
          <div className="user-dropdown__profile">
            <span className="user-avatar lg">{initial}</span>
            <div className="user-dropdown__info">
              <span className="user-dropdown__name">{displayName || email.split('@')[0]}</span>
              <span className="user-dropdown__email">{email}</span>
            </div>
          </div>
          <div className="user-dropdown__divider" />
          <MenuItem icon={IconDashboard} label="Dashboard" onClick={() => { setOpen(false); onDashboard?.(); }} />
          {user.admin && <MenuItem icon={IconDashboard} label="Admin Dashboard" onClick={() => { setOpen(false); onAdmin?.(); }} />}
          <MenuItem icon={IconSettings}  label="Settings"  onClick={() => { setOpen(false); onSettings?.(); }} />
          <div className="user-dropdown__divider" />
          <MenuItem icon={IconLogout} label="Sign Out" onClick={() => { setOpen(false); onLogout?.(); }} danger />
      </PopupLayer>
    </div>
  );
}

function MenuItem({ icon: Icon, label, onClick, danger }) {
  return (
    <button className={`user-dropdown__item${danger ? ' danger' : ''}`} onClick={onClick}>
      <Icon size={14} />
      <span>{label}</span>
    </button>
  );
}
