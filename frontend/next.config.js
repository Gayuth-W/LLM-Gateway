/**
 * The browser calls same-origin /gateway/* and Next proxies to the gateway server-side,
 * so there is no CORS and the backend needs no changes. Override the target with
 * GATEWAY_URL if the gateway runs somewhere other than localhost:8080.
 */
const gatewayUrl = process.env.GATEWAY_URL || 'http://localhost:8080';

/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      { source: '/gateway/:path*', destination: `${gatewayUrl}/:path*` },
    ];
  },
};

module.exports = nextConfig;
