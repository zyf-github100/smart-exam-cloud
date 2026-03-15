# smart-exam-web

## Run locally

```bash
npm install
npm run dev
```

The frontend defaults to `/api/v1` as its API base and uses the Vite dev server proxy for local development.

## Local frontend + remote backend

This is the recommended way to run the frontend on your local machine while the backend is deployed on a server.

1. Copy `.env.example` to `.env.local`.
2. Set `VITE_API_PROXY_TARGET` to your backend gateway address, for example `http://36.137.84.162:9000`.
3. Start the frontend with `npm run dev`.
4. Keep the API base as `/api/v1` in the login or connection panel.

With this setup, the browser only talks to the local Vite server, and Vite forwards `/api/**` requests to the remote gateway. That avoids browser CORS issues.

## Backend deployment notes

- The backend gateway must be reachable from your local machine on `9000`, or through `80/443` behind Nginx.
- The backend templates under `docs/nacos/` now default to `127.0.0.1` and runtime env placeholders. Override them through `.env.runtime` or server env vars instead of rewriting template files.
- Do not expose MySQL, Redis, RabbitMQ, or Nacos management ports to the public internet unless you explicitly need them.
