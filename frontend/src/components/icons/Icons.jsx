/* Minimal inline SVG icon set — consistent 16×16 viewport, 1.5px stroke */
const Icon = ({ d, size = 16, filled = false, ...props }) => (
  <svg
    width={size} height={size}
    viewBox="0 0 16 16"
    fill={filled ? 'currentColor' : 'none'}
    stroke={filled ? 'none' : 'currentColor'}
    strokeWidth="1.5"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    {...props}
  >
    {Array.isArray(d)
      ? d.map((p, i) => <path key={i} d={p} />)
      : <path d={d} />}
  </svg>
);

export const IconArrowLeft  = (p) => <Icon d="M10 13L5 8l5-5" {...p} />;
export const IconArrowRight = (p) => <Icon d="M6 3l5 5-5 5" {...p} />;
export const IconRefresh    = (p) => <Icon d="M2 8a6 6 0 1 1 1.5 4" {...p} />;
export const IconStop       = (p) => <Icon d="M4 4h8v8H4z" {...p} />;
export const IconDownload   = (p) => <Icon d={['M8 1v9', 'M4.5 7.5L8 11l3.5-3.5', 'M2 11v3h12v-3']} {...p} />;
export const IconHome       = (p) => <Icon d={['M1 7l7-5 7 5', 'M3 6v7h4v-3h2v3h4V6']} {...p} />;
export const IconPlus       = (p) => <Icon d="M8 2v12M2 8h12" {...p} />;
export const IconX          = (p) => <Icon d="M3 3l10 10M13 3L3 13" {...p} />;
export const IconShield     = (p) => <Icon d="M8 1L2 4v4c0 3.5 2.5 6 6 7 3.5-1 6-3.5 6-7V4z" {...p} />;
export const IconLock       = (p) => <Icon d={['M4 7V5a4 4 0 0 1 8 0v2', 'M2 7h12a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z']} {...p} />;
export const IconGlobe      = (p) => <Icon d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1zM1 8h14M8 1c-2 2-3 4.5-3 7s1 5 3 7M8 1c2 2 3 4.5 3 7s-1 5-3 7" {...p} />;
export const IconSearch     = (p) => <Icon d="M11 11l3 3M2 7a5 5 0 1 0 10 0A5 5 0 0 0 2 7z" {...p} />;
export const IconHistory    = (p) => <Icon d={['M1 8a7 7 0 1 0 14 0A7 7 0 0 0 1 8z', 'M8 5v4l2.5 2.5']} {...p} />;
export const IconBookmark   = (p) => <Icon d="M4 2h8a1 1 0 0 1 1 1v11l-5-3-5 3V3a1 1 0 0 1 1-1z" {...p} />;
export const IconBookmarks  = (p) => <Icon d={['M3 4h8a1 1 0 0 1 1 1v9l-4.5-2.7L3 14V5a1 1 0 0 1 0-1z', 'M6 2h7a1 1 0 0 1 1 1v9']} {...p} />;
export const IconDashboard  = (p) => <Icon d={['M1 1h6v6H1z', 'M9 1h6v6H9z', 'M1 9h6v6H1z', 'M9 9h6v6H9z']} {...p} />;
export const IconSettings   = (p) => <Icon d="M8 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM13.6 8a5.8 5.8 0 0 0 .1-1l1.3-.7-1.4-2.4-1.3.5A5.7 5.7 0 0 0 10 3.5l-.2-1.5H6.2L6 3.5A5.7 5.7 0 0 0 3.7 4.4l-1.3-.5L1 6.3l1.3.7A5.8 5.8 0 0 0 2.2 8c0 .3 0 .7.1 1L1 9.7l1.4 2.4 1.3-.5A5.7 5.7 0 0 0 6 12.5l.2 1.5h3.6l.2-1.5a5.7 5.7 0 0 0 2.3-.9l1.3.5 1.4-2.4z" {...p} />;
export const IconUser       = (p) => <Icon d={['M8 7a3 3 0 1 0 0-6 3 3 0 0 0 0 6z', 'M2 15a6 6 0 0 1 12 0']} {...p} />;
export const IconLogout     = (p) => <Icon d={['M6 2H3a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3', 'M10 11l4-3-4-3', 'M6 8h8']} {...p} />;
export const IconStar       = (p) => <Icon d="M8 1l1.8 3.6 4 .6-2.9 2.8.7 4-3.6-1.9L4 12l.7-4L1.8 5.2l4-.6z" {...p} />;
export const IconMoon       = (p) => <Icon d="M12 3a6 6 0 0 1-9 9 6 6 0 0 0 9-9z" {...p} />;
export const IconSun        = (p) => <Icon d="M8 1v2M8 13v2M1 8h2M13 8h2M3.2 3.2l1.4 1.4M11.4 11.4l1.4 1.4M3.2 12.8l1.4-1.4M11.4 4.6l1.4-1.4M8 5a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" {...p} />;
export const IconEye        = (p) => <Icon d={['M1 8s2.5-5 7-5 7 5 7 5', 'M1 8s2.5 5 7 5 7-5 7-5', 'M8 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4z']} {...p} />;
export const IconEyeOff     = (p) => <Icon d={['M2 2l12 12', 'M9.9 9.9A3 3 0 0 1 4.1 6.1', 'M5.1 4.1A7 7 0 0 1 15 8s-.8 1.7-2.1 3', 'M1 8s1.4 3 4 4.3']} {...p} />;
export const IconInfo       = (p) => <Icon d={['M8 7v4', 'M8 5v.5']} {...p} />;
export const IconCheck      = (p) => <Icon d="M2 8l4 4 8-8" {...p} />;
export const IconChevronDown= (p) => <Icon d="M4 6l4 4 4-4" {...p} />;
export const IconMenu       = (p) => <Icon d="M2 4h12M2 8h12M2 12h12" {...p} />;
export const IconTrash      = (p) => <Icon d={['M2 4h12', 'M5 4V3h6v1', 'M4 4l1 9h6l1-9']} {...p} />;
export const IconExternalLink=(p) => <Icon d={['M7 3H3a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1V9', 'M9 1h6v6', 'M9 7l6-6']} {...p} />;
export const IconCopy       = (p) => <Icon d={['M5 5h-2a1 1 0 0 0-1 1v7a1 1 0 0 0 1 1h7a1 1 0 0 0 1-1v-2', 'M7 1h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1z']} {...p} />;
