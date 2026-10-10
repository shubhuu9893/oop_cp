import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { adminApi } from '../services/api.js';
import './AdminDashboardPage.css';

export default function AdminDashboardPage() {
  const [page, setPage] = useState({ users: [], nextPageToken: '' });
  const [count, setCount] = useState(null);
  const [token, setToken] = useState('');
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const load = useCallback(async (next = '') => {
    setBusy(true); setError('');
    try {
      const [users, summary] = await Promise.all([adminApi.getUsers(50, next), adminApi.getSummary()]);
      setPage(users); setCount(summary.totalUsers); setToken(next);
    } catch (e) { setError(e.status === 403 ? 'This account is not authorized as an administrator.' : e.status === 503 ? 'Firebase Admin credentials are not configured on the Java backend.' : `Could not load the Firebase user directory: ${e.message}`); }
    finally { setBusy(false); }
  }, []);
  useEffect(() => { load(); }, [load]);
  const filtered = useMemo(() => page.users.filter(user => [user.email, user.displayName, user.uid].filter(Boolean).join(' ').toLowerCase().includes(query.toLowerCase())), [page.users, query]);
  return <section className="admin-page">
    <header className="admin-page__header"><div><h1>Administrator dashboard</h1><p>Firebase Authentication account directory</p></div><button className="admin-page__refresh" onClick={() => load(token)} disabled={busy}>Refresh</button></header>
    {error ? <p className="admin-page__error" role="alert">{error}</p> : <>
      <div className="admin-page__summary"><span>Registered users</span><strong>{busy && count == null ? '…' : count ?? '—'}</strong></div>
      <div className="admin-page__tools"><input aria-label="Search users" value={query} onChange={e => setQuery(e.target.value)} placeholder="Search name, email, or UID" /></div>
      {busy && <p className="admin-page__state">Loading users…</p>}
      {!busy && filtered.length === 0 && <p className="admin-page__state">{query ? 'No users match this search.' : 'No Firebase users are registered.'}</p>}
      {filtered.length > 0 && <div className="admin-page__table-wrap"><table><thead><tr><th>User</th><th>Email</th><th>Firebase UID</th><th>Created</th><th>Provider</th><th>Status</th></tr></thead><tbody>{filtered.map(user => <tr key={user.uid}><td>{user.displayName || '—'}</td><td>{user.email || '—'}</td><td className="admin-page__uid">{user.uid}</td><td>{user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '—'}</td><td>{user.providers?.join(', ') || '—'}</td><td>{user.disabled ? 'Disabled' : 'Active'}{user.emailVerified ? ' · verified' : ''}</td></tr>)}</tbody></table></div>}
      <footer className="admin-page__pagination"><button onClick={() => load(page.nextPageToken)} disabled={busy || !page.nextPageToken}>Next page</button><span>{filtered.length} shown · {count ?? 0} total</span></footer>
    </>}
  </section>;
}
