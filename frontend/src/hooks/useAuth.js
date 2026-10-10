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
    const local = { uid: firebaseUser.uid, email: firebaseUser.email || '', displayName: firebaseUser.displayName || '', loggedIn: true, admin: false };
    setUser(local);
    try {
      const verified = await authApi.getUser();
      setOffline(false);
      setUser({ ...local, ...verified });
    } catch (error) {
      setOffline(isOfflineError(error));
      // Retain actual Firebase client identity for the signed-in UI, but never infer admin.
      setUser(local);
    } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (!auth) { setLoading(false); setUser(null); return undefined; }
    return firebaseAuth.subscribe(enrichUser);
  }, [enrichUser]);

  const login = useCallback(async (email, password) => {
    try { await firebaseAuth.signIn(email, password); }
    catch (error) { throw authFailure('Email sign-in', error); }
  }, []);

  const register = useCallback(async (email, password) => {
    try { await firebaseAuth.register(email, password); }
    catch (error) { throw authFailure('Email registration', error); }
  }, []);

  const loginGoogle = useCallback(async () => {
    try { await firebaseAuth.signInGoogle(); }
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
