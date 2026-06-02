SET @service_col_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = 'admin_db'
      AND table_name = 'sys_audit_log'
      AND column_name = 'service_name'
);
SET @service_col_sql = IF(
    @service_col_exists = 0,
    'ALTER TABLE admin_db.sys_audit_log ADD COLUMN service_name VARCHAR(64) NOT NULL DEFAULT ''unknown-service'' AFTER id',
    'SELECT 1'
);
PREPARE service_col_stmt FROM @service_col_sql;
EXECUTE service_col_stmt;
DEALLOCATE PREPARE service_col_stmt;

SET @module_col_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = 'admin_db'
      AND table_name = 'sys_audit_log'
      AND column_name = 'module_key'
);
SET @module_col_sql = IF(
    @module_col_exists = 0,
    'ALTER TABLE admin_db.sys_audit_log ADD COLUMN module_key VARCHAR(64) NOT NULL DEFAULT ''GENERAL'' AFTER service_name',
    'SELECT 1'
);
PREPARE module_col_stmt FROM @module_col_sql;
EXECUTE module_col_stmt;
DEALLOCATE PREPARE module_col_stmt;

SET @service_idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = 'admin_db'
      AND table_name = 'sys_audit_log'
      AND index_name = 'idx_sys_audit_log_service_created'
);
SET @service_idx_sql = IF(
    @service_idx_exists = 0,
    'CREATE INDEX idx_sys_audit_log_service_created ON admin_db.sys_audit_log (service_name, created_at)',
    'SELECT 1'
);
PREPARE service_idx_stmt FROM @service_idx_sql;
EXECUTE service_idx_stmt;
DEALLOCATE PREPARE service_idx_stmt;

SET @module_idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = 'admin_db'
      AND table_name = 'sys_audit_log'
      AND index_name = 'idx_sys_audit_log_module_created'
);
SET @module_idx_sql = IF(
    @module_idx_exists = 0,
    'CREATE INDEX idx_sys_audit_log_module_created ON admin_db.sys_audit_log (module_key, created_at)',
    'SELECT 1'
);
PREPARE module_idx_stmt FROM @module_idx_sql;
EXECUTE module_idx_stmt;
DEALLOCATE PREPARE module_idx_stmt;

UPDATE admin_db.sys_audit_log
SET service_name = 'admin-service',
    module_key = 'ADMIN'
WHERE service_name = 'unknown-service'
  AND module_key = 'GENERAL';
