# Nacos Config Center Setup

This project now loads configuration from Nacos by default:

- `common.yaml`
- `${spring.application.name}.yaml`

All services read these from:

- `group`: `DEFAULT_GROUP` (override with `NACOS_GROUP`)
- `namespace`: default public namespace (override with `NACOS_NAMESPACE`)
- `server`: `192.168.242.10:8848` (override with `NACOS_SERVER_ADDR`)

> Important for Nacos 2.x:
> When services run outside the Nacos container network (for example on host IDE),
> make sure Docker publishes `8848`, `9848`, `9849`.
> `8848` is HTTP API/UI, `9848/9849` are gRPC channels used by Nacos Java client.

## 1. Import Data IDs in Nacos UI

Open `http://192.168.242.10:8848/nacos`, then create these Data IDs in `DEFAULT_GROUP` with type `YAML`:

- `common.yaml`
- `gateway-service.yaml`
- `auth-service.yaml`
- `user-service.yaml`
- `question-service.yaml`
- `exam-service.yaml`
- `grading-service.yaml`
- `analysis-service.yaml`
- `admin-service.yaml`

Use files from this folder as content templates.

## 2. Script Import (Recommended)

### 2.1 Windows PowerShell

```powershell
.\docs\nacos\import-nacos.ps1 `
  -NacosAddr "http://127.0.0.1:8848" `
  -Group "DEFAULT_GROUP" `
  -Username "nacos" `
  -Password "nacos"
```

If you use namespace, add:

```powershell
-Namespace "<your-namespace-id>"
```

### 2.2 Linux/macOS/Git Bash

```bash
chmod +x docs/nacos/import-nacos.sh
NACOS_ADDR="http://127.0.0.1:8848" \
NACOS_GROUP="DEFAULT_GROUP" \
NACOS_USERNAME="nacos" \
NACOS_PASSWORD="nacos" \
./docs/nacos/import-nacos.sh
```

If you use namespace, set `NACOS_NAMESPACE=<your-namespace-id>`.

## 3. Optional Manual CLI Import

```bash
NACOS_ADDR="http://192.168.242.10:8848"
GROUP="DEFAULT_GROUP"
for f in common.yaml gateway-service.yaml auth-service.yaml user-service.yaml question-service.yaml exam-service.yaml grading-service.yaml analysis-service.yaml admin-service.yaml; do
  curl -sS -X POST "${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${f}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@docs/nacos/${f}"
done
```

## 4. Runtime Environment Variables

If your Nacos address/account changes, set these before starting services:

- `NACOS_SERVER_ADDR`
- `NACOS_USERNAME`
- `NACOS_PASSWORD`
- `NACOS_GROUP`
- `NACOS_NAMESPACE`

## 5. MQ Reliability Config Keys

`common.yaml` now includes RabbitMQ reliability defaults used by exam/grading/analysis services:

- `spring.rabbitmq.publisher-confirm-type=correlated`
- `spring.rabbitmq.publisher-returns=true`
- `spring.rabbitmq.template.mandatory=true`
- `spring.rabbitmq.listener.simple.acknowledge-mode=manual`
- `smart-exam.mq.exam-submitted.max-retries`
- `smart-exam.mq.exam-submitted.retry-ttl-ms`
- `smart-exam.mq.score-published.max-retries`
- `smart-exam.mq.score-published.retry-ttl-ms`

## 6. Exam Status Auto-Transition Keys

`exam-service.yaml` includes scheduler keys for automatic status transition:

- `smart-exam.exam.status-sync-initial-delay-ms`
- `smart-exam.exam.status-sync-interval-ms`
