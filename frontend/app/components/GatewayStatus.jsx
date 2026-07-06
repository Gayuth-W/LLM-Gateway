'use client';

import { useEffect, useState } from 'react';
import { getGatewayInfo } from '../lib/api';

export default function GatewayStatus() {
  const [state, setState] = useState('checking'); // checking | up | down

  useEffect(() => {
    let active = true;
    const check = async () => {
      try {
        await getGatewayInfo();
        if (active) setState('up');
      } catch {
        if (active) setState('down');
      }
    };
    check();
    const id = setInterval(check, 5000);
    return () => { active = false; clearInterval(id); };
  }, []);

  const label = state === 'up' ? 'Connected' : state === 'down' ? 'Unreachable' : 'Checking…';
  return (
    <div className={`gw-status ${state}`} title="GET /">
      <span className="gw-dot" />
      <span className="gw-label">{label}</span>
      <span className="gw-target">:8080</span>
    </div>
  );
}
