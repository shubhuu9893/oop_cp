import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';

const parsePort = (value, fallback) => {
  const port = Number(value);
  return Number.isInteger(port) && port >= 1024 && port <= 65535 ? port : fallback;
};
export default defineConfig(({ mode }) => {
  // Read root .env but explicitly expose only Firebase's public web configuration.
  const env = { ...loadEnv(mode, resolve(process.cwd(), '..'), ''), ...process.env };
  const vitePort = parsePort(env.VITE_PORT || env.CACHEBROWSE_FRONTEND_PORT, 5173);
  const apiTarget = env.VITE_API_TARGET || `http://127.0.0.1:${parsePort(env.CACHEBROWSE_API_PORT, 8080)}`;
  const firebaseValue = (key) => env[`VITE_${key}`] || env[key] || '';
  const firebaseConfig = {
    apiKey: firebaseValue('FIREBASE_API_KEY'),
    authDomain: firebaseValue('FIREBASE_AUTH_DOMAIN'),
    projectId: firebaseValue('FIREBASE_PROJECT_ID'),
    storageBucket: firebaseValue('FIREBASE_STORAGE_BUCKET'),
    messagingSenderId: firebaseValue('FIREBASE_MESSAGING_SENDER_ID'),
    appId: firebaseValue('FIREBASE_APP_ID'),
  };
  return {
  plugins: [react()],
  define: { 'import.meta.env.VITE_FIREBASE_CONFIG': JSON.stringify(JSON.stringify(firebaseConfig)) },
  server: {
    host: 'localhost',
    port: vitePort,
    strictPort: true,
    // Firebase popup auth needs the opener relationship to remain available
    // while the provider window is closing. This is not an auth bypass and
    // does not expose backend credentials to the browser.
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin-allow-popups',
    },
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path,
      },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  };
});
