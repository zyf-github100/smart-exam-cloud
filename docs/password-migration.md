# 历史明文密码迁移 Runbook

本 Runbook 用于把 `user_db.sys_user.password_hash` 中的历史明文密码离线迁移为 BCrypt，并完成 `auth-service` 灰度开关收口。

## 1. 当前收口基线

- `auth-service` 仅接受 BCrypt 密码登录，不再支持“登录成功后顺手升级明文密码”。
- `ALLOW_LEGACY_PLAIN_PASSWORD` 已废弃；若环境变量或 Nacos 中仍配置该开关，`auth-service` 会直接拒绝启动。
- `auth-service` 启动时会校验 `sys_user.password_hash` 是否仍存在非 BCrypt 记录；若存在，服务拒绝启动并输出样例用户名。
- 管理员重置密码接口继续写入 BCrypt，因此迁移完成后旧版本 / 新版本的 `admin-service` 都能兼容迁移后的数据。

## 2. 准备工作

1. 进入维护窗口，停止登录入口与密码写入入口，至少包含：
   - `auth-service`
   - `gateway-service`
   - `admin-service`
2. 为 `user_db.sys_user` 做库级备份，推荐至少保留一份完整 `mysqldump`：

```bash
mysqldump --default-character-set=utf8mb4 \
  -h 127.0.0.1 -P 3306 -u smart_exam_app -p \
  user_db sys_user > backup-sys_user-before-password-cutover.sql
```

3. 确认运行环境具备：
   - JDK 17+
   - Maven 3.9+
   - 项目源码或已同步的部署仓库

## 3. Dry Run 盘点

PowerShell：

```powershell
.\scripts\security\migrate-legacy-passwords.ps1 `
  -Mode dry-run `
  -ReportFile runtime-logs/password-migration-report.json
```

Bash：

```bash
./scripts/security/migrate-legacy-passwords.sh \
  --mode=dry-run \
  --report-file=runtime-logs/password-migration-report.json
```

说明：

- 脚本默认会优先读取项目根目录 `.env.runtime` 中的 `MYSQL_*` 配置。
- 若未设置 `MYSQL_URL`，脚本会自动按 `MYSQL_HOST` / `MYSQL_PORT` 拼接 `user_db` 的 JDBC URL。
- Dry run 只读取数据，不会修改数据库。
- 结果会输出到控制台，并写入 `runtime-logs/password-migration-report.json`。

关注以下字段：

- `legacyUserCountBefore`
- `sampleLegacyUsernamesBefore`
- `cutoverReady`

若只想做小批量试迁移，可追加 `--limit=50`（Bash）或 `-Limit 50`（PowerShell）先观察首批账号。

## 4. 执行迁移

PowerShell：

```powershell
.\scripts\security\migrate-legacy-passwords.ps1 `
  -Mode migrate `
  -ReportFile runtime-logs/password-migration-report.json `
  -RollbackSql runtime-logs/password-migration-rollback.sql
```

Bash：

```bash
./scripts/security/migrate-legacy-passwords.sh \
  --mode=migrate \
  --report-file=runtime-logs/password-migration-report.json \
  --rollback-sql=runtime-logs/password-migration-rollback.sql
```

说明：

- `--rollback-sql` / `-RollbackSql` 可选，但强烈建议在维护窗口保留。
- 回滚 SQL 中保存的是“可恢复原明文密码”的 Base64 形式，属于高敏感文件。
- 建议将回滚 SQL 放在受限目录，仅授予运维负责人读取权限；验收完成后及时销毁。
- `batch-size` 默认为 `200`，可按数据库负载调整。

迁移脚本会：

1. 读取所有 `password_hash NOT LIKE '$2%'` 的账号。
2. 将其密码离线改写为 BCrypt。
3. 输出迁移报告。
4. 若指定回滚文件，则额外生成逐条 `UPDATE` 的回滚 SQL。

## 5. 迁移后校验

执行以下 SQL，确认结果为 `0`：

```sql
SELECT COUNT(1)
FROM sys_user
WHERE password_hash IS NOT NULL
  AND password_hash NOT LIKE '$2%';
```

同时检查 `runtime-logs/password-migration-report.json`：

- `legacyUserCountAfter = 0`
- `migratedUserCount` 与预期一致
- `cutoverReady = true`

## 6. 发布与重启顺序

1. 确认 Nacos 与环境变量中不再配置 `ALLOW_LEGACY_PLAIN_PASSWORD`。
2. 发布包含本次收口逻辑的 `auth-service`。
3. 依次启动：
   - `auth-service`
   - `gateway-service`
   - `admin-service`
4. 使用管理员、教师、学生账号各做一次登录冒烟。

若 `auth-service` 启动失败：

- 先看日志是否报遗留开关未删除。
- 再看是否仍检测到 legacy plain-password records。
- 不要通过恢复开关绕过；应先补跑迁移或回滚数据。

## 7. 回滚策略

### 7.1 应用回滚

- 迁移后的密码都是 BCrypt，旧版与新版认证逻辑都能识别 BCrypt。
- 因此若只是 `auth-service` / `gateway-service` 二进制回滚，通常不需要回滚数据库。

### 7.2 数据回滚

仅在以下场景考虑回滚数据库：

- 迁移批次执行过程中出现业务侧不可接受的异常。
- 需要恢复到“迁移前的历史数据视图”做专项排查。

优先级建议：

1. 优先使用维护窗口前的 `mysqldump` / 数据库快照整体恢复。
2. 若仅需恢复本次脚本处理过的账号，可执行生成的 `password-migration-rollback.sql`。

注意：

- 回滚 SQL 能恢复原始明文密码，必须视同密钥材料管理。
- 回滚完成后，如果仍要继续上线新版本 `auth-service`，必须重新完成离线迁移并确认 legacy 计数归零。
- 不建议把 `ALLOW_LEGACY_PLAIN_PASSWORD` 当作回滚手段；该开关已被设计为启动即失败，目的就是阻止迁移窗口反复打开。

## 8. 常用补充参数

- `--limit=<N>` / `-Limit <N>`：只处理前 N 个历史账号，适合先做小批量演练。
- `--batch-size=<N>` / `-BatchSize <N>`：控制 JDBC batch 大小。
- `--sample-size=<N>` / `-SampleSize <N>`：控制报告中展示的样例用户名数量。
- `--jdbc-url=...` / `-JdbcUrl ...`：显式指定数据库地址，不走 `.env.runtime`。
- `--skip-env-runtime` / `-SkipEnvRuntime`：跳过读取 `.env.runtime`。
