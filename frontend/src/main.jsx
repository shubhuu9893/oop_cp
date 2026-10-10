import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import OverlayApp from './OverlayApp.jsx';
import './styles/variables.css';
import './styles/reset.css';
import './styles/base.css';

class AppErrorBoundary extends React.Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error: error instanceof Error ? error.message : 'Unknown UI initialization error' };
  }

  componentDidCatch(error, info) {
    console.error('[UI] React application failed to render', error, info.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <main role="alert" style={{ minHeight: '100vh', padding: 40, background: '#0f172a', color: '#f8fafc', fontFamily: 'system-ui,sans-serif' }}>
        <h1>CacheBrowse could not render</h1>
        <p>The browser controls could not initialize. The error is recorded in the desktop renderer console.</p>
        <p>{this.state.error}</p>
        <button onClick={() => window.location.reload()} style={{ marginTop: 16, padding: '8px 14px' }}>Reload UI</button>
      </main>
    );
  }
}

window.addEventListener('error', event => {
  console.error('[UI] Uncaught renderer error', event.error || event.message);
});
window.addEventListener('unhandledrejection', event => {
  console.error('[UI] Unhandled renderer promise rejection', event.reason);
});

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('CacheBrowse startup failed: #root element is missing.');

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <AppErrorBoundary>
      {new URLSearchParams(window.location.search).has('cachebrowseOverlay')
        ? <OverlayApp />
        : <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><App /></BrowserRouter>}
    </AppErrorBoundary>
  </React.StrictMode>
);
