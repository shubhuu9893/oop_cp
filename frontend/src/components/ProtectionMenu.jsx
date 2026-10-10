import React, { useState, useRef, useCallback } from 'react';
import { IconShield, IconCheck, IconX } from './icons/Icons.jsx';
import PopupLayer from './PopupLayer.jsx';
import './ProtectionMenu.css';

export default function ProtectionMenu({ protection = {}, stats = {}, onToggleAdBlock, onToggleTrackerBlock }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  const close = useCallback(() => setOpen(false), []);

  const runtime = protection?.runtime;
  const available = protection != null && typeof protection?.adBlock === 'boolean';
  const runtimeAvailable = runtime?.available === true;
  const adOn = runtimeAvailable && runtime.adBlockEnforcing === true;
  const trkOn = runtimeAvailable && runtime.trackerBlockEnforcing === true;
  const bothOn = adOn && trkOn;
  const protectionLabel = (configured, enforcing) => {
    if (!available || !runtimeAvailable) return 'UNVERIFIED';
    if (enforcing) return 'ON';
    return configured ? 'NOT ACTIVE' : 'OFF';
  };
  const filterStatus = !runtimeAvailable
    ? 'Chromium enforcement status unavailable'
    : !runtime.requestFilteringReady
      ? 'Chromium request filter is not installed'
      : runtime.filterLists?.ads === 'maintained' && runtime.filterLists?.trackers === 'maintained'
        ? 'Maintained filter lists active'
        : 'Built-in fallback rules active; filter lists unavailable';

  return (
    <div className="protection-menu" ref={ref}>
      <button
        className={`toolbar-btn protection-trigger${bothOn ? ' protected' : ' warning'}`}
        ref={ref}
        onClick={() => setOpen(o => !o)}
        title={runtimeAvailable ? 'CacheBrowse Protection is active in Chromium' : 'Chromium protection status unavailable'}
        aria-label="CacheBrowse Protection"
        aria-expanded={open}
      >
        <IconShield size={15} />
        <span className={`protection-dot ${bothOn ? 'green' : 'yellow'}`} />
      </button>

      <PopupLayer
        anchorRef={ref} open={open} onClose={close} className="protection-popup fade-in"
        overlayKind="protection"
        overlayData={{
          adBlock: protection?.adBlock === true,
          trackerBlock: protection?.trackerBlock === true,
          adEnforcing: adOn,
          trackerEnforcing: trkOn,
          status: filterStatus,
          adsBlocked: stats?.adsBlocked ?? '—',
          trackersBlocked: stats?.trackersBlocked ?? '—',
          blockedRequests: stats?.blockedRequests ?? '—',
        }}
        onOverlayAction={action => {
          if (action === 'toggle-ad') onToggleAdBlock?.();
          if (action === 'toggle-tracker') onToggleTrackerBlock?.();
        }}
      >
          <div className="protection-popup__header">
            <IconShield size={16} style={{ color: 'var(--accent-secondary)' }} />
            <span>CacheBrowse Protection</span>
          </div>

          <div className="protection-popup__row">
            <div className="protection-popup__label">
              <span>Ad Blocking</span>
              <span className={`badge ${runtimeAvailable ? (adOn ? 'badge-success' : 'badge-danger') : ''}`}>{protectionLabel(protection?.adBlock, adOn)}</span>
            </div>
            <button aria-label="Toggle ad blocking" aria-pressed={protection?.adBlock === true} className={`protection-toggle ${protection?.adBlock ? 'on' : 'off'}`} onClick={onToggleAdBlock}>
              <span className="protection-toggle__thumb" />
            </button>
          </div>

          <div className="protection-popup__row">
            <div className="protection-popup__label">
              <span>Tracker Blocking</span>
              <span className={`badge ${runtimeAvailable ? (trkOn ? 'badge-success' : 'badge-danger') : ''}`}>{protectionLabel(protection?.trackerBlock, trkOn)}</span>
            </div>
            <button aria-label="Toggle tracker blocking" aria-pressed={protection?.trackerBlock === true} className={`protection-toggle ${protection?.trackerBlock ? 'on' : 'off'}`} onClick={onToggleTrackerBlock}>
              <span className="protection-toggle__thumb" />
            </button>
          </div>

          <div className="protection-popup__divider" />

          <div className="protection-runtime-status" role="status">{filterStatus}</div>

          <div className="protection-popup__stats">
            <StatRow label="Ads Blocked"       value={stats?.adsBlocked ?? '—'}      color="cyan"    />
            <StatRow label="Trackers Blocked"  value={stats?.trackersBlocked ?? '—'} color="purple"  />
            <StatRow label="Requests Blocked"  value={stats?.blockedRequests ?? '—'} color="danger"  />
          </div>
      </PopupLayer>
    </div>
  );
}

function StatRow({ label, value, color }) {
  return (
    <div className="stat-row">
      <span className="stat-row__label">{label}</span>
      <span className={`stat-row__value text-${color}`}>{value}</span>
    </div>
  );
}
