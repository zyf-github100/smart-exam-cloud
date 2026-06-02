# Deploy Scripts

Versioned deployment helpers live in this directory. Secrets stay outside git.

## 1. Prepare local secret files

```powershell
Copy-Item scripts/deploy/servers.example.json scripts/deploy/servers.local.json
```

Fill `servers.local.json` with the actual SSH passwords, or provide the same JSON through `SMART_EXAM_SERVERS_JSON`.

For GitHub Actions CD, add these repository secrets:

- `SMART_EXAM_SERVERS_JSON`: server JSON. For the current all-in-one server, use `36.138.84.84` as both backend and frontend host, then fill the real SSH username/password.
- `DEPLOY_BACKEND_POST_COMMAND`: backend restart command, for example `supervisorctl restart auth-service user-service question-service exam-service grading-service analysis-service admin-service gateway-service`.
- `DEPLOY_FRONTEND_POST_COMMAND`: frontend restart command. If omitted, the deploy script defaults to `docker restart smart-exam-web >/dev/null`.
- `SMOKE_GATEWAY_URL`: optional, defaults to `http://36.138.84.84:9000`.
- `SMOKE_USERNAME` / `SMOKE_PASSWORD`: optional smoke-check login account. If either is missing, smoke check is skipped.

Add repository variable `CD_ENABLED=true` only after the secrets above are ready. Before that, the CD workflow can still be started manually with `workflow_dispatch`, but it will not auto-deploy after CI.

Example `SMART_EXAM_SERVERS_JSON`:

```json
{
  "backend": {
    "label": "Backend",
    "host": "36.138.84.84",
    "port": 22,
    "username": "root",
    "password": "replace-with-backend-password"
  },
  "frontend": {
    "label": "Frontend",
    "host": "36.138.84.84",
    "port": 22,
    "username": "root",
    "password": "replace-with-frontend-password"
  }
}
```

The first CD version is defined in `.github/workflows/cd.yml`. It runs after CI succeeds on `main`, and can also be started manually from GitHub Actions.

## 2. Build and deploy

```powershell
python scripts/deploy/deploy-smart-exam.py both --build `
  --backend-post-command "supervisorctl restart auth-service question-service gateway-service" `
  --frontend-post-command "docker restart smart-exam-web >/dev/null"
```

The deploy script uploads:

- backend jars
- `docker-compose.yml`
- `.env.example`
- `.env.runtime.example`
- `docs/nacos/*`
- frontend `dist/`

## 3. Smoke check

```powershell
python scripts/deploy/smoke-check.py `
  --gateway http://127.0.0.1:9000 `
  --username student001 `
  --password 123456
```

Adjust the gateway address and account according to the deployed environment.
