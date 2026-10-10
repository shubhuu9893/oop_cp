import React, { useEffect, useRef } from 'react';

export default function BrowserViewport({ error, onRetry, onBack, url }) {
  const viewportRef = useRef(null);

  useEffect(() => {
    const node = viewportRef.current;
    const bridge = window.cacheBrowse;
    if (!node || !bridge) return undefined;

    const measure = () => {
      const rect = node.getBoundingClientRect();
      bridge.setPageBounds({ x: rect.left, y: rect.top, width: rect.width, height: rect.height });
    };
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    window.addEventListener('resize', measure);
    const unsubscribe = bridge.subscribe('layoutNeeded', measure);
    measure();
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', measure);
      unsubscribe();
    };
  }, []);

  return (
    <div className="browser-viewport" ref={viewportRef}>
      {!window.cacheBrowse && !error && (
        <section className="web-mode-notice" role="status">
          <div className="web-mode-notice__icon" aria-hidden="true">◉</div>
          <h1>CacheBrowse Chromium is not running</h1>
          <p>This Vite-only page cannot render arbitrary websites. Launch the CacheBrowse desktop browser to load this destination in its protected Chromium tab.</p>
          <p className="web-mode-notice__url">{url}</p>
          <p className="web-mode-notice__note">From the frontend directory, run <code>npm run dev</code>. This starts the React UI, Java API, and the in-app Chromium host.</p>
        </section>
      )}
      {error && (
        <div className="page-error" role="alert">
          <h1>Unable to load this page</h1>
          <p>{error}</p>
          <div>
            <button onClick={onRetry}>Retry</button>
            <button className="secondary" onClick={onBack}>Go back</button>
          </div>
        </div>
      )}
    </div>
  );
}
