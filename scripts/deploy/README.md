# Deploy Scripts

Versioned deployment helpers live in this directory. Secrets stay outside git.

## 1. Prepare local secret files

```powershell
Copy-Item scripts/deploy/servers.example.json scripts/deploy/servers.local.json
```

Fill `servers.local.json` with the actual SSH passwords, or provide the same JSON through `SMART_EXAM_SERVERS_JSON`.

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
