import { useState, useCallback, useEffect } from 'react';
import { searchApi, settingsApi } from '../services/api.js';

export function useBrowser(activeTab) {
  const [pendingUrl, setPendingUrl] = useState('');
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState(null);
  const [lastInput, setLastInput] = useState('');
  const url = activeTab?.url || pendingUrl;
  const loading = resolving || activeTab?.loading === true;

  useEffect(() => {
    setPendingUrl('');
    setError(activeTab?.error || null);
  }, [activeTab?.id, activeTab?.url, activeTab?.error]);

  const navigate = useCallback(async input => {
    if (!input?.trim()) return;
    const tabId = activeTab?.id;
    if (!tabId) {
      setError('No active CacheBrowse tab is available.');
      return;
    }
    setLastInput(input.trim());
    setPendingUrl('');
    setResolving(true);
    setError(null);
    try {
      if (!window.cacheBrowse) {
        throw new Error('The in-app Chromium engine is not running. Start CacheBrowse with `npm run dev` from the frontend directory.');
      }
      const resolved = await searchApi.resolve(input.trim());
      setPendingUrl(resolved.url);
      await window.cacheBrowse.navigate(tabId, resolved.url);
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    } finally {
      setResolving(false);
    }
  }, [activeTab?.id]);

  const goBack = useCallback(() => activeTab && window.cacheBrowse?.back(activeTab.id), [activeTab]);
  const goForward = useCallback(() => activeTab && window.cacheBrowse?.forward(activeTab.id), [activeTab]);
  const reload = useCallback(() => activeTab && window.cacheBrowse?.reload(activeTab.id), [activeTab]);
  const stop = useCallback(() => activeTab && window.cacheBrowse?.stop(activeTab.id), [activeTab]);
  const home = useCallback(async () => {
    if (!activeTab) return;
    if (!window.cacheBrowse) {
      setError('The in-app Chromium engine is not running. Start CacheBrowse with `npm run dev` from the frontend directory.');
      return;
    }
    setPendingUrl('');
    setError(null);
    try {
      const settings = await settingsApi.get();
      const homePage = settings?.homePage?.trim();
      if (homePage && homePage !== 'cachebrowse://home') {
        const resolved = await searchApi.resolve(homePage);
        setPendingUrl(resolved.url);
        await window.cacheBrowse.navigate(activeTab.id, resolved.url);
      } else {
        await window.cacheBrowse.home(activeTab.id);
      }
    } catch (e) {
      setError(e.message);
    }
  }, [activeTab?.id]);

  return {
    url: pendingUrl || activeTab?.url || '',
    lastInput,
    loading,
    error,
    canGoBack: activeTab?.canGoBack === true,
    canGoForward: activeTab?.canGoForward === true,
    navigate,
    goBack,
    goForward,
    reload,
    stop,
    home,
  };
}
