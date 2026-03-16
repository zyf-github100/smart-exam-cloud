USE question_db;

SET @schema_name = 'question_db';
SET @table_name = 'q_question';

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = @table_name
      AND index_name = 'idx_q_question_type_created_at'
  ),
  'SELECT 1',
  'CREATE INDEX idx_q_question_type_created_at ON question_db.q_question (type, created_at)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = @table_name
      AND index_name = 'idx_q_question_knowledge_point_created_at'
  ),
  'SELECT 1',
  'CREATE INDEX idx_q_question_knowledge_point_created_at ON question_db.q_question (knowledge_point, created_at)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = @table_name
      AND index_name = 'idx_q_question_created_by_type_created_at'
  ),
  'SELECT 1',
  'CREATE INDEX idx_q_question_created_by_type_created_at ON question_db.q_question (created_by, type, created_at)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = @table_name
      AND index_name = 'idx_q_question_created_by_knowledge_point_created_at'
  ),
  'SELECT 1',
  'CREATE INDEX idx_q_question_created_by_knowledge_point_created_at ON question_db.q_question (created_by, knowledge_point, created_at)'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
