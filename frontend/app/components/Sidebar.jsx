'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import GatewayStatus from './GatewayStatus';

const NAV = [
  { href: '/', label: 'Playground' },
  { href: '/teams', label: 'Teams' },
  { href: '/health', label: 'Provider health' },
  { href: '/spending', label: 'Spending' },
];

export default function Sidebar() {
  const path = usePathname();
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
        <div className="foot-key">Java 21 · Spring WebFlux · Ollama</div>
      </div>
    </aside>
  );
}
