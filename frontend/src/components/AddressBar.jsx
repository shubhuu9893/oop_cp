import React, { useState, useRef, useEffect } from 'react';
import { IconLock, IconGlobe, IconSearch, IconRefresh, IconCopy } from './icons/Icons.jsx';
import './AddressBar.css';

export default function AddressBar({ url = '', loading, onNavigate }) {
  const [value, setValue]   = useState(url);
  const [focused, setFocused] = useState(false);
  const inputRef = useRef(null);

  useEffect(() => {
    if (!focused) setValue(url);
  }, [url, focused]);

  const isSecure = url.startsWith('https://');
  const isLocal  = url === '' || url.startsWith('cachebrowse://');

  const handleSubmit = (e) => {
    e.preventDefault();
    const input = value.trim();
    if (!input) return;
    onNavigate?.(input);
    inputRef.current?.blur();
  };

  const handleFocus = () => {
    setFocused(true);
    setTimeout(() => inputRef.current?.select(), 10);
  };

  const handleCopy = () => {
    if (url) navigator.clipboard.writeText(url).catch(() => {});
  };

  return (
    <form className={`address-bar${focused ? ' focused' : ''}`} onSubmit={handleSubmit}>
      <button type="button" className="address-bar__security" onClick={handleCopy} title={isSecure ? 'Secure connection — click to copy' : 'Click to copy URL'}>
        {loading ? <span className="spinner" style={{ width: 14, height: 14 }} /> :
          isLocal ? <IconSearch size={14} /> :
          isSecure ? <IconLock size={14} style={{ color: 'var(--accent-success)' }} /> :
          <IconGlobe size={14} style={{ color: 'var(--accent-warning)' }} />}
      </button>

      <input
        ref={inputRef}
        type="text"
        className="address-bar__input"
        value={focused ? value : (url || '')}
        onChange={e => setValue(e.target.value)}
        onFocus={handleFocus}
        onBlur={() => setFocused(false)}
        placeholder="Search the web or enter a URL"
        spellCheck={false}
        autoComplete="off"
        autoCorrect="off"
      />

      {focused && value && (
        <button type="submit" className="address-bar__go" title="Go">
          ↵
        </button>
      )}
    </form>
  );
}
