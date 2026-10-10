import React, { useState } from 'react';
import { IconEye, IconEyeOff, IconShield } from '../components/icons/Icons.jsx';
import './LoginPage.css';

export default function LoginPage({ onLogin, onRegister, onGoogle, onForgotPassword, configurationError = '' }) {
  const [mode, setMode]   = useState('login'); // 'login' | 'register'
  const [email, setEmail] = useState('');
  const [pass, setPass]   = useState('');
  const [show, setShow]   = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy]   = useState(false);
  const [notice, setNotice] = useState('');

  const handle = async (e) => {
    e.preventDefault();
    if (!email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) { setError('Enter a valid email address'); return; }
    if (!pass) { setError('Password is required'); return; }
    if (mode === 'register' && pass.length < 6) { setError('Password must be at least 6 characters'); return; }
    setBusy(true); setError('');
    try {
      if (mode === 'login') await onLogin(email.trim(), pass);
      else                  await onRegister(email.trim(), pass);
    } catch (err) {
      setError(err.message || (mode === 'login' ? 'Login failed' : 'Registration failed'));
    } finally {
      setBusy(false);
    }
  };

  const google = async () => {
    setBusy(true); setError(''); setNotice('');
    try { await onGoogle?.(); }
    catch (err) { setError(err.message || 'Google sign-in failed'); }
    finally { setBusy(false); }
  };

  const forgot = async () => {
    if (!email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('Enter your email address first to receive a reset link.'); return;
    }
    setBusy(true); setError(''); setNotice('');
    try { await onForgotPassword?.(email.trim()); setNotice('Password reset email sent. Check your inbox.'); }
    catch (err) { setError(err.message || 'Could not send reset email'); }
    finally { setBusy(false); }
  };

  return (
    <div className="login-page">
      <div className="login-bg" aria-hidden="true" />
      <div className="login-card">
        <div className="login-brand">
          <div className="login-brand__icon">
            <IconShield size={28} style={{ color: 'var(--accent-secondary)' }} />
          </div>
          <h1 className="login-brand__name">CacheBrowse</h1>
          <p className="login-brand__sub">Smart Caching Browser</p>
        </div>

        <form className="login-form" onSubmit={handle} noValidate>
          <h2 className="login-form__title">
            {mode === 'login' ? 'Sign in to your account' : 'Create an account'}
          </h2>

          {configurationError && <p className="login-error" role="alert">{configurationError}</p>}

          <div className="login-field">
            <label className="login-field__label" htmlFor="email">Email</label>
            <input
              id="email" type="email" autoComplete="email" autoFocus
              className="login-field__input"
              placeholder="you@example.com"
              value={email} onChange={e => setEmail(e.target.value)}
            />
          </div>

          <div className="login-field">
            <label className="login-field__label" htmlFor="password">Password</label>
            <div className="login-field__wrap">
              <input
                id="password" type={show ? 'text' : 'password'}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                className="login-field__input"
                placeholder="••••••••"
                value={pass} onChange={e => setPass(e.target.value)}
              />
      <button type="button" className="login-field__eye" aria-label={show ? 'Hide password' : 'Show password'} onClick={() => setShow(s => !s)}>
                {show ? <IconEyeOff size={14} /> : <IconEye size={14} />}
              </button>
            </div>
          </div>

          {mode === 'login' && <button type="button" className="login-forgot" onClick={forgot} disabled={busy}>Forgot password?</button>}

          {error && <p className="login-error">{error}</p>}
          {notice && <p className="login-notice" role="status">{notice}</p>}

          <button type="submit" className="login-submit" disabled={busy}>
            {busy ? <span className="spinner" style={{ borderColor: 'rgba(255,255,255,.3)', borderTopColor: 'white' }} /> : null}
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign In' : 'Create Account'}
          </button>

          <div className="login-divider"><span>or</span></div>
          <button type="button" className="login-google" onClick={google} disabled={busy}>
            <GoogleMark /> Continue with Google
          </button>

          <p className="login-switch">
            {mode === 'login' ? "Don't have an account? " : 'Already have an account? '}
            <button type="button" onClick={() => { setMode(m => m === 'login' ? 'register' : 'login'); setError(''); }}>
              {mode === 'login' ? 'Sign Up' : 'Sign In'}
            </button>
          </p>
        </form>
      </div>
    </div>
  );
}

function GoogleMark() {
  return <svg width="16" height="16" viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.25 5.48-4.73 7.18l7.62 5.91c4.45-4.11 7.15-10.15 7.15-17.56z"/><path fill="#FBBC05" d="M10.53 28.59a14.3 14.3 0 0 1 0-9.18l-7.98-6.19a23.9 23.9 0 0 0 0 21.56l7.98-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.9-5.89l-7.62-5.91c-2.12 1.42-4.84 2.26-8.28 2.26-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>;
}
