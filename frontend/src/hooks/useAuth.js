import { useState, useEffect, useCallback } from 'react';
import { authApi, isOfflineError } from '../services/api.js';
import { auth, firebaseAuth, firebaseErrorMessage, getFirebaseDiagnostic } from '../services/firebase.js';

function authFailure(operation, error) {
  const code = error?.code || error?.name || 'unknown';
  const message = String(error?.message || '')
    .replace(/(key=)[^&\s)]+/gi, '$1[redacted]')
    .replace(/Bearer\s+[^\s)]+/gi, 'Bearer [redacted]')
    .replace(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g, '[email]');
  console.error(`[Firebase] ${operation} failed code=${code} message=${message || '(empty)'}`);
  return new Error(firebaseErrorMessage(error));
}

export function useAuth() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [offline, setOffline] = useState(false);

  const enrichUser = useCallback(async firebaseUser => {
    if (!firebaseUser) { setUser(null); setOffline(false); setLoading(false); return; }
    const local = { uid: firebaseUser.uid, email: firebaseUser.email || '', displayName: firebaseUser.displayName || '', loggedIn: true };
    setUser(local);
    try {
      const verified = await authApi.getUser();
      setOffline(false);
      setUser({ ...local, ...verified });
    } catch (error) {
      setOffline(isOfflineError(error));
      // A Firebase client session is not sufficient for protected API access.
      // If the backend explicitly rejects verification or is not configured,
      // fail closed instead of showing a user who cannot load private data.
      if (error?.status === 401 || error?.status === 403 || error?.status === 503) {
        setUser(null);
      } else {
        // A temporary network/backend outage may recover without signing out.
        setUser(local);
      }
    } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (!auth) { setLoading(false); setUser(null); return undefined; }
    return firebaseAuth.subscribe(enrichUser);
  }, [enrichUser]);

  const login = useCallback(async (email, password) => {
    try {
      await firebaseAuth.signIn(email, password);
      await authApi.getUser();
    }
    catch (error) { throw authFailure('Email sign-in', error); }
  }, []);

  const register = useCallback(async (email, password) => {
    try {
      await firebaseAuth.register(email, password);
      await authApi.getUser();
    }
    catch (error) { throw authFailure('Email registration', error); }
  }, []);

  const loginGoogle = useCallback(async () => {
    try {
      const signedInUser = await firebaseAuth.signInGoogle();
      // Redirect sign-in returns null and is completed by the auth listener.
      if (signedInUser) await authApi.getUser();
    }
    catch (error) { throw authFailure('Google sign-in', error); }
  }, []);

  const resetPassword = useCallback(async email => {
    try { await firebaseAuth.sendPasswordReset(email); }
    catch (error) { throw authFailure('Password reset', error); }
  }, []);

  const logout = useCallback(async () => {
    try { await firebaseAuth.signOut(); }
    catch (error) { throw authFailure('Sign-out', error); }
    setUser(null);
  }, []);

  return { user, loading, offline, configError: getFirebaseDiagnostic(), login, register, loginGoogle, resetPassword, logout };
}
