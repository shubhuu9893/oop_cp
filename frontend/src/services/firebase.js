import { getApps, initializeApp } from 'firebase/app';
import {
  browserLocalPersistence,
  createUserWithEmailAndPassword,
  getAuth,
  GoogleAuthProvider,
  sendPasswordResetEmail,
  setPersistence,
  signInWithEmailAndPassword,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
} from 'firebase/auth';
import { parseFirebaseConfig, validateFirebaseConfig } from './firebaseConfig.js';

export const firebaseConfig = parseFirebaseConfig(import.meta.env.VITE_FIREBASE_CONFIG || '{}');
export const firebaseConfigError = validateFirebaseConfig(firebaseConfig);
export const firebaseConfigured = !firebaseConfigError;
let auth = null;
let initializationError = '';
if (firebaseConfigured) {
  try {
    const existing = getApps().find(candidate => candidate.name === '[DEFAULT]');
    if (existing) {
      const fields = ['apiKey', 'authDomain', 'projectId', 'messagingSenderId', 'appId'];
      const mismatch = fields.some(key => (existing.options[key] || '') !== (firebaseConfig[key] || ''));
      if (mismatch) throw new Error('Firebase was already initialized with different settings. Restart Vite after correcting .env.');
    }
    const app = existing || initializeApp(firebaseConfig);
    auth = getAuth(app);
  } catch (error) {
    initializationError = error?.message || 'Firebase could not initialize.';
    console.error('[Firebase] Initialization failed', error?.code || error?.name || 'unknown');
  }
}
export { auth };
export function getFirebaseDiagnostic() {
  return auth ? '' : initializationError || firebaseConfigError;
}

let persistenceErrorCode = '';
const persistenceReady = auth
  ? setPersistence(auth, browserLocalPersistence).then(() => true).catch(error => {
    persistenceErrorCode = error?.code || 'unknown';
    console.error('[Firebase] Local session persistence could not be enabled', persistenceErrorCode);
    return false;
  })
  : Promise.resolve(false);
if (auth) {
  persistenceReady.then(enabled => {
    if (enabled) return getRedirectResult(auth);
    return null;
  }).catch(error => {
    console.warn('[Firebase] Redirect sign-in did not complete', error?.code || error?.name || 'unknown');
  });
}
async function requireAuth() {
  if (!auth) throw new Error(initializationError || firebaseConfigError);
  if (!await persistenceReady) {
    throw Object.assign(new Error('Firebase could not save the sign-in session in this browser. Enable site storage and try again.'), {
      code: persistenceErrorCode || 'auth/web-storage-unsupported',
    });
  }
  return auth;
}

export const firebaseAuth = {
  async signIn(email, password) {
    const instance = await requireAuth();
    return (await signInWithEmailAndPassword(instance, email, password)).user;
  },
  async register(email, password) {
    const instance = await requireAuth();
    return (await createUserWithEmailAndPassword(instance, email, password)).user;
  },
  async signInGoogle() {
    const instance = await requireAuth();
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    try { return (await signInWithPopup(instance, provider)).user; }
    catch (error) {
      if (['auth/popup-blocked', 'auth/operation-not-supported-in-this-environment'].includes(error?.code)) {
        await signInWithRedirect(instance, provider);
        return null;
      }
      throw error;
    }
  },
  async sendPasswordReset(email) {
    const instance = await requireAuth();
    return sendPasswordResetEmail(instance, email);
  },
  async signOut() {
    if (auth) await signOut(auth);
  },
  subscribe(callback) {
    if (!auth) { callback(null); return () => {}; }
    return auth.onAuthStateChanged(callback);
  },
};

export function firebaseErrorMessage(error) {
  const messages = {
    'auth/invalid-credential': 'Email or password is incorrect.',
    'auth/user-not-found': 'No account was found for that email.',
    'auth/wrong-password': 'Email or password is incorrect.',
    'auth/email-already-in-use': 'An account already exists for that email.',
    'auth/weak-password': 'Choose a stronger password (at least 6 characters).',
    'auth/invalid-email': 'Enter a valid email address.',
    'auth/popup-closed-by-user': 'Google sign-in was cancelled.',
    'auth/cancelled-popup-request': 'A Google sign-in window is already open. Finish or close it before retrying.',
    'auth/popup-blocked': 'The sign-in popup was blocked. Allow popups and try again.',
    'auth/unauthorized-domain': 'This app domain is not authorized in Firebase Authentication settings.',
    'auth/operation-not-allowed': 'This sign-in provider is not enabled in Firebase Console.',
    'auth/invalid-api-key': 'Firebase rejected the Web API key. Verify the key and its API restrictions in Google Cloud Console.',
    'auth/project-not-found': 'Firebase could not find this project. Verify the project ID and web app configuration.',
    'auth/app-not-authorized': 'This Firebase web app is not authorized to use Authentication. Check the API key restrictions and enabled Identity Toolkit API.',
    'auth/web-storage-unsupported': 'Browser storage is unavailable, so Firebase cannot persist this session. Enable site storage and try again.',
    'auth/internal-error': 'Firebase returned an internal authentication error. Check the Firebase error code in the console, confirm the API key/project match, and verify the selected provider is enabled. No credentials were logged.',
    'cachebrowse/firebase-not-configured': 'Firebase is not ready. Check the public Firebase settings in the root .env file, then restart Vite.',
    'auth/account-exists-with-different-credential': 'This email already uses another sign-in method. Sign in with it first, then link Google in account settings.',
    'auth/network-request-failed': 'Could not reach Firebase. Check your network connection.',
  };
  if (messages[error?.code]) return messages[error.code];
  return error?.message || 'Authentication failed. Please try again.';
}
