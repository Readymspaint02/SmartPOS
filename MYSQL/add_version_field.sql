-- ============================================================
-- 乐观锁迁移脚本 - 为inventory表添加version字段
-- ============================================================
-- 
-- 执行时间：2025-01-11
-- 功能说明：为inventory表添加version字段，实现乐观锁机制
-- 
-- 面试考点：
-- - Q1：为什么用INT类型？
--   A1：version从0开始，每次更新+1，INT足够存储。
-- 
-- - Q2：默认值为什么是0？
--   A2：初始状态，第一次更新后变为1。
-- 
-- - Q3：是否需要索引？
--   A3：不需要。version只在UPDATE时使用，WHERE条件已包含主键。
-- 
-- 使用方式：
-- mysql -u root -p gbs_commerce_system_02 < add_version_field.sql
-- ============================================================

USE gbs_commerce_system_02;

-- 1. 检查version字段是否存在
SELECT COUNT(*) AS version_column_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'gbs_commerce_system_02' 
  AND TABLE_NAME = 'inventory' 
  AND COLUMN_NAME = 'version';

-- 2. 添加version字段（如果不存在）
ALTER TABLE inventory 
ADD COLUMN IF NOT EXISTS version INT DEFAULT 0 COMMENT '乐观锁版本号';

-- 3. 初始化version字段为0
UPDATE inventory SET version = 0 WHERE version IS NULL;

-- 4. 验证结果
SELECT 
    id,
    product_id,
    stock_quantity,
    version
FROM inventory
LIMIT 10;

-- 5. 完成提示
SELECT '乐观锁字段添加完成！' AS message;
