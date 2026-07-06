// Streaming is proxied through a Route Handler (not next.config rewrites) because
// the dev rewrite proxy buffers the response, which flattens SSE into one lump.
// This passes the upstream stream straight through, unbuffered.
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const GATEWAY = process.env.GATEWAY_URL || 'http://localhost:8080';

export async function POST(req) {
  const { apiKey, priority, payload } = await req.json();

  const upstream = await fetch(`${GATEWAY}/v1/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
      'X-Priority': priority || 'high',
    },
    body: JSON.stringify(payload),
    duplex: 'half',
  });

  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      'Content-Type': upstream.headers.get('Content-Type') || 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
    },
  });
}
