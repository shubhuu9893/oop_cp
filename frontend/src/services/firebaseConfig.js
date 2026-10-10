const REQUIRED_FIELDS = ['apiKey', 'authDomain', 'projectId', 'messagingSenderId', 'appId'];

export function validateFirebaseConfig(config) {
  if (!config || typeof config !== 'object' || Array.isArray(config)) {
    return 'Firebase configuration is missing or invalid. Check the public Firebase values in the project .env file.';
  }
  const missing = REQUIRED_FIELDS.filter(key => typeof config[key] !== 'string' || !config[key].trim());
  if (missing.length) {
    return `Firebase configuration is incomplete (missing ${missing.join(', ')}). Check the project .env file and restart Vite.`;
  }
  if (!/^\d+$/.test(config.messagingSenderId)) {
    return 'FIREBASE_MESSAGING_SENDER_ID must contain digits only; check for stray quotes or whitespace in .env.';
  }
  const appId = config.appId.match(/^\d+:(\d+):[a-z0-9]+:.+$/i);
  if (!appId || appId[1] !== config.messagingSenderId) {
    return 'Firebase app ID and messaging sender ID do not match. Copy both values from the same Firebase web app.';
  }
  if (!/^AIza[\w-]{30,}$/.test(config.apiKey)) {
    return 'FIREBASE_API_KEY has an invalid format. Copy the Web API key from the Firebase project settings.';
  }
  try {
    const domain = new URL(`https://${config.authDomain}`).hostname;
    if (domain !== config.authDomain || config.authDomain.includes('/')) throw new Error('invalid host');
  } catch {
    return 'FIREBASE_AUTH_DOMAIN must be a hostname such as your-project.firebaseapp.com.';
  }
  return '';
}

export function parseFirebaseConfig(raw) {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return Object.fromEntries(Object.entries(parsed).map(([key, value]) => [
      key, typeof value === 'string' ? value.trim() : value,
    ]));
  } catch {
    return {};
  }
}
