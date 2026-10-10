import { useState, useEffect, useCallback, useRef } from 'react';
import { statsApi, protectionApi, isOfflineError } from '../services/api.js';

export function useStats(pollIntervalMs = 4000) {
  const [stats, setStats]           = useState(null);
  const [protection, setProtection] = useState(null);
  const [error, setError] = useState(null);
  const requestRef = useRef(null);

  const fetchStats = useCallback(() => {
    if (requestRef.current) return requestRef.current;
    const request = (async () => {
      try {
        const [s, p] = await Promise.all([statsApi.get(), protectionApi.getStatus()]);
        if (s) setStats(s);
        if (p) {
          let runtime = { available: false };
          try {
            runtime = await window.cacheBrowse?.getProtectionRuntimeStatus?.() || runtime;
          } catch { /* Settings remain visible; enforcement is explicitly unverified. */ }
          setProtection({ ...p, runtime });
        }
        setError(null);
        return true;
      } catch (err) {
        setError(isOfflineError(err) ? 'Java backend is unavailable' : err.message);
        return false;
      }
    })();
    requestRef.current = request;
    request.finally(() => {
      if (requestRef.current === request) requestRef.current = null;
    });
    return request;
  }, []);

  useEffect(() => {
    let disposed = false;
    let inFlight = false;
    let failures = 0;
    let timer;

    const poll = async () => {
      if (disposed || inFlight) return;
      inFlight = true;
      const ok = await fetchStats();
      inFlight = false;
      if (disposed) return;
      if (ok) {
        failures = 0;
        timer = setTimeout(poll, pollIntervalMs);
      } else {
        failures += 1;
        if (failures <= 3) timer = setTimeout(poll, Math.min(pollIntervalMs * (2 ** failures), 30_000));
      }
    };

    const unsubscribe = window.cacheBrowse?.subscribe('backendStatus', status => {
      if (!status?.online || disposed) return;
      clearTimeout(timer);
      failures = 0;
      poll();
    });
    poll();
    return () => {
      disposed = true;
      clearTimeout(timer);
      unsubscribe?.();
    };
  }, [fetchStats, pollIntervalMs]);

  const toggleAdBlock = useCallback(async () => {
    const next = protection?.adBlock !== true;
    await protectionApi.setAdBlock(next);
    setProtection(p => ({ ...p, adBlock: next }));
  }, [protection?.adBlock]);

  const toggleTrackerBlock = useCallback(async () => {
    const next = protection?.trackerBlock !== true;
    await protectionApi.setTrackerBlock(next);
    setProtection(p => ({ ...p, trackerBlock: next }));
  }, [protection?.trackerBlock]);

  return { stats, protection, error, toggleAdBlock, toggleTrackerBlock, refetch: fetchStats };
}
