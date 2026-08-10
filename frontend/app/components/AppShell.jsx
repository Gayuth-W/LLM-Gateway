'use client';

import { usePathname } from 'next/navigation';
import Sidebar from './Sidebar';
import { useAuth } from './AuthProvider';

/**
 * Decides what frame the page gets. The sign-in screen is full-bleed with no
 * sidebar; everything else is the two-column console and requires a session.
 *
 * This lives in a client component because the root layout is a server
 * component and cannot read the session.
 */
export default function AppShell({ children }) {
  const { status } = useAuth();
  const isLogin = usePathname() === '/login';

  if (status === 'checking') {
    return <div className="boot">Checking your session…</div>;
  }

  if (isLogin) {
    return children;
  }

  if (status !== 'authed') {
    // The guard in AuthProvider is already redirecting; this is the frame or two
    // in between, and it should not look like an error.
    return <div className="boot">Taking you to sign in…</div>;
  }

  return (
    <div className="app">
      <Sidebar />
      <main className="content">{children}</main>
    </div>
  );
}
