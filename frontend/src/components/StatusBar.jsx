import React from 'react';
import './StatusBar.css';

export default function StatusBar({ stats, statusText }) {
  const ads     = stats ? stats.adsBlocked ?? 0 : '—';
  const trackers= stats ? stats.trackersBlocked ?? 0 : '—';

  return (
    <div className="statusbar">
      <span className="statusbar__text">{statusText || '● Ready'}</span>
      <div className="statusbar__sep" />
      <StatItem label="Ads"      value={ads}                color="cyan"    />
      <StatItem label="Trackers" value={trackers}           color="purple"  />
    </div>
  );
}

function StatItem({ label, value, color }) {
  return (
    <span className={`statusbar__item statusbar__item--${color}`}>
      <span className="statusbar__label">{label}</span>
      <span className="statusbar__value">{value}</span>
    </span>
  );
}
