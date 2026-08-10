'use client';

import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { login as apiLogin, getMe } from '../lib/api';
import {
  AUTH_EXPIRED,
  clearSession,
  getStoredUser,
  getToken,
  roleAllows,
  saveSession,
} from '../lib/auth';

/**
 * Holds the operator session and guards the console routes.
 *
 * Status is a three-state, not a boolean: 'checking' exists so the app never
 * flashes the sign-in screen at someone who is already signed in while the
 * stored token is being revalidated.
 *
 * The stored token is verified against GET /auth/me on load rather than trusted
 * on sight. A token that expired while the tab was closed is then caught once,
 * here, instead of turning every page into a broken request.
 */
const AuthContext = createContext(null);

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

export default function AuthProvider({ children }) {
  const router = useRouter();
  const pathname = usePathname();

  const [status, setStatus] = useState('checking'); // checking | authed | anon
  const [user, setUser] = useState(null);

  // Revalidate whatever is in storage, once, on load.
  useEffect(() => {
    let cancelled = false;

    const token = getToken();
    if (!token) {
      setStatus('anon');
      return undefined;
    }

    // Render optimistically from the cached identity so the shell does not
    // flicker, then correct it from the server.
    setUser(getStoredUser());

    getMe()
      .then((me) => {
        if (cancelled) return;
        saveSession(token, me);
        setUser(me);
        setStatus('authed');
      })
      .catch(() => {
        if (cancelled) return;
        clearSession();
        setUser(null);
        setStatus('anon');
      });

    return () => { cancelled = true; };
  }, []);

  // api.js fires this when the gateway rejects the token mid-session.
  useEffect(() => {
    function onExpired() {
      setUser(null);
      setStatus('anon');
    }
    window.addEventListener(AUTH_EXPIRED, onExpired);
    return () => window.removeEventListener(AUTH_EXPIRED, onExpired);
  }, []);

  // Route guard. Every console page needs a session; /login is the one exception.
  useEffect(() => {
    if (status === 'checking') return;
    if (status === 'anon' && pathname !== '/login') {
      router.replace('/login');
    } else if (status === 'authed' && pathname === '/login') {
      router.replace('/');
    }
  }, [status, pathname, router]);

  const signIn = useCallback(async (username, password) => {
    const res = await apiLogin(username, password);
    const me = { username: res.username, role: res.role };
    saveSession(res.token, me);
    setUser(me);
    setStatus('authed');
    return me;
  }, []);

  const signOut = useCallback(() => {
    // Nothing to call server-side: tokens are stateless and expire on their own.
    clearSession();
    setUser(null);
    setStatus('anon');
    router.replace('/login');
  }, [router]);

  /**
   * Whether the signed-in role reaches `required`. This only decides which
   * controls to render — the gateway makes the real decision and will answer
   * 403 regardless of what this returns.
   */
  const can = useCallback((required) => roleAllows(user?.role, required), [user]);

  return (
    <AuthContext.Provider value={{ status, user, signIn, signOut, can }}>
      {children}
    </AuthContext.Provider>
  );
}
