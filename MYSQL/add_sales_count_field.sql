-- ============================================================
-- 销量字段迁移脚本 - 为product表添加sales_count字段
-- ============================================================
-- 
-- 功能说明：为product表添加sales_count字段，用于商品销量统计
-- 
-- 面试考点：
-- - Q1：为什么用INT类型？
--   A1：销量为非负整数，INT足够存储。
-- 
-- - Q2：默认值为什么是0？
--   A2：新商品初始销量为0。
-- 
-- - Q3：是否需要索引？
--   A3：需要。推荐商品查询按sales_count降序排序，添加索引可提升排序性能。
-- 
-- 使用方式：
-- mysql -u root -p gbs_commerce_system_02 < add_sales_count_field.sql
-- ============================================================

USE gbs_commerce_system_02;

-- 1. 添加sales_count字段（如果不存在）
ALTER TABLE product 
ADD COLUMN IF NOT EXISTS sales_count INT DEFAULT 0 COMMENT '销量统计';

-- 2. 初始化sales_count字段为0
UPDATE product SET sales_count = 0 WHERE sales_count IS NULL;

-- 3. 添加索引（如果不存在），优化ORDER BY sales_count DESC查询
CREATE INDEX IF NOT EXISTS idx_sales_count ON product(sales_count DESC);

-- 4. 验证结果
SELECT 
    id,
    product_name,
    sales_count
FROM product
LIMIT 10;

-- 5. 完成提示
SELECT '销量字段添加完成！' AS message;
