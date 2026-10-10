import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const viteEntry = path.join(path.dirname(require.resolve('vite/package.json')), 'bin/vite.js');
const electronCli = require.resolve('electron/cli.js');
const parsePort = (value, fallback) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 1024 && parsed <= 65535 ? parsed : fallback;
};
const port = parsePort(process.env.VITE_PORT || process.env.CACHEBROWSE_FRONTEND_PORT, 5173);
const apiPort = parsePort(process.env.CACHEBROWSE_API_PORT, 8080);
if (port === apiPort) throw new Error('Vite and Java API ports must be different.');
// Firebase Authentication treats 127.0.0.1 and localhost as different OAuth
// redirect domains. `localhost` is the standard Firebase development domain.
const url = `http://localhost:${port}`;
const children = [];

function startChild(command, args, env = process.env, onExit = null) {
  const child = spawn(command, args, { cwd: frontendDir, env, stdio: 'inherit', windowsHide: true });
  children.push(child);
  child.on('exit', code => {
    if (onExit) onExit(code);
    if (code !== null && code !== 0) shutdown(code);
  });
  return child;
}

async function waitForVite() {
  for (let i = 0; i < 80; i++) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1000) });
      if (response.ok) return;
    } catch { /* Vite is still starting. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`Vite did not become ready at ${url}`);
}

let shuttingDown = false;
function shutdown(code = 0) {
  if (shuttingDown) return;
  shuttingDown = true;
  for (const child of children) {
    if (child.exitCode === null) child.kill();
  }
  setTimeout(() => process.exit(code), 500).unref();
}

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));

try {
  startChild(process.execPath, [viteEntry, '--host', 'localhost', '--port', String(port), '--strictPort'], {
    ...process.env,
    VITE_PORT: String(port),
    CACHEBROWSE_FRONTEND_PORT: String(port),
  });
  await waitForVite();
  console.log(`CacheBrowse UI: ${url}`);
  startChild(process.execPath, [electronCli, frontendDir], {
    ...process.env,
    VITE_DEV_SERVER_URL: `${url}/`,
    VITE_PORT: String(port),
    CACHEBROWSE_FRONTEND_PORT: String(port),
  }, () => shutdown(0));
} catch (error) {
  console.error(error.message);
  shutdown(1);
}
