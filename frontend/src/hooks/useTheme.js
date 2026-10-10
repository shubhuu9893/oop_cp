import { useState, useEffect } from 'react';

export function useTheme() {
  const [theme, setTheme] = useState(
    () => {
      try { return window.localStorage.getItem('cb-theme') || 'dark'; }
      catch { return 'dark'; }
    }
  );

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    try { window.localStorage.setItem('cb-theme', theme); }
    catch (error) { console.warn('[UI] Theme preference could not be persisted', error); }
  }, [theme]);

  const toggle = () => setTheme(t => (t === 'dark' ? 'light' : 'dark'));

  return { theme, toggle, isDark: theme === 'dark' };
}
