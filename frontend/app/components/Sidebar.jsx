'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import GatewayStatus from './GatewayStatus';
import { useAuth } from './AuthProvider';

const NAV = [
  { href: '/', label: 'Playground' },
  { href: '/teams', label: 'Teams' },
  { href: '/health', label: 'Provider health' },
  { href: '/spending', label: 'Spending' },
];

export default function Sidebar() {
  const path = usePathname();
  const { user, signOut } = useAuth();

  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-name">LLM&nbsp;Gateway</div>
        <div className="brand-sub">operator console</div>
      </div>

      <nav className="nav">
        {NAV.map((n) => {
          const active = n.href === '/' ? path === '/' : path.startsWith(n.href);
          return (
            <Link key={n.href} href={n.href} className={`nav-item${active ? ' active' : ''}`}>
              {n.label}
            </Link>
          );
        })}
      </nav>

      <div className="sidebar-foot">
        <GatewayStatus />

        {user && (
          <div className="session">
            <div className="session-who">
              <div className="session-name">{user.username}</div>
              <span className={`role-badge ${user.role.toLowerCase()}`}>{user.role}</span>
            </div>
            <button className="btn tiny" onClick={signOut}>Sign out</button>
          </div>
        )}

        <div className="foot-key">Java 21 · Spring WebFlux · Ollama</div>
      </div>
    </aside>
  );
}
