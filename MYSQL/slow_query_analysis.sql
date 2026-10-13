-- ============================================================
-- 【MySQL-慢SQL分析】慢查询配置与分析脚本
-- ============================================================
-- 
-- 文件作用：
-- 配置MySQL慢查询日志，定位性能瓶颈SQL。
-- 分析工具：EXPLAIN、慢查询日志、mysqldumpslow。
-- 
-- 面试考点：
-- - Q1：如何定位慢SQL？
--   A1：1. 开启慢查询日志：slow_query_log = ON
--       2. 设置阈值：long_query_time = 2（秒）
--       3. 分析日志：mysqldumpslow 或 pt-query-digest
--       4. 使用 EXPLAIN 分析执行计划
-- 
-- - Q2：EXPLAIN关注哪些字段？
--   A2：- type：访问类型（ALL全表扫描、index、range、ref、const）
--       - key：使用的索引
--       - rows：预计扫描行数
--       - Extra：额外信息（Using filesort需优化、Using temporary需优化）
-- 
-- - Q3：type字段的含义？
--   A3：- ALL：全表扫描（性能最差）
--       - index：扫描索引树（比ALL好）
--       - range：范围扫描（如WHERE age > 18）
--       - ref：非唯一索引扫描
--       - const：常量，最多一条匹配（性能最好）
-- 
-- - Q4：Extra字段的含义？
--   A4：- Using filesort：需要额外排序（建议加索引）
--       - Using temporary：使用临时表（建议优化SQL）
--       - Using index：覆盖索引（性能好）
--       - Using where：WHERE过滤
-- 
-- 参考文档：
-- - 简历-八股文-必备.txt 4.3 慢SQL分析
-- ============================================================

-- ============================================================
-- 1. 开启慢查询日志（需要修改MySQL配置文件）
-- ============================================================

-- 方式一：临时开启（重启后失效）
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 2;  -- 超过2秒的查询记录为慢查询
SET GLOBAL slow_query_log_file = '/var/log/mysql/mysql-slow.log';

-- 方式二：永久开启（修改my.cnf配置文件）
-- [mysqld]
-- slow_query_log = ON
-- long_query_time = 2
-- slow_query_log_file = /var/log/mysql/mysql-slow.log
-- log_queries_not_using_indexes = ON  -- 记录未使用索引的查询

-- 查看慢查询日志配置
SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
SHOW VARIABLES LIKE 'slow_query_log_file';


-- ============================================================
-- 2. EXPLAIN 分析示例
-- ============================================================

-- 示例1：分析订单查询（按订单号查询）
EXPLAIN SELECT * FROM orders WHERE order_no = '20251029144902269094';
-- 期望结果：
-- type: const（常量，性能最好）
-- key: uniq_order_no（使用唯一索引）
-- rows: 1（只扫描1行）

-- 示例2：分析订单查询（按会员ID查询）
EXPLAIN SELECT * FROM orders WHERE member_id = 'M001';
-- 期望结果：
-- type: ref（非唯一索引扫描）
-- key: idx_member_id（使用会员索引）
-- rows: 预计扫描行数

-- 示例3：分析订单查询（按状态查询）
EXPLAIN SELECT * FROM orders WHERE order_status = 1;
-- 期望结果：
-- type: ref（非唯一索引扫描）
-- key: idx_order_status（使用状态索引）
-- rows: 预计扫描行数

-- 示例4：分析订单查询（组合条件）
EXPLAIN SELECT * FROM orders 
WHERE member_id = 'M001' AND order_status = 1 
ORDER BY create_time DESC;
-- 期望结果：
-- type: ref
-- key: idx_member_status_createtime（使用组合索引）
-- Extra: Using index（覆盖索引，性能好）

-- 示例5：分析商品查询（按分类+状态查询）
EXPLAIN SELECT * FROM products 
WHERE category_id = 1 AND status = 0 
ORDER BY create_time DESC;
-- 期望结果：
-- type: ref
-- key: idx_category_status（使用组合索引）
-- Extra: Using index（覆盖索引，性能好）

-- 示例6：分析全表扫描（未使用索引）
EXPLAIN SELECT * FROM products WHERE product_name LIKE '%可乐%';
-- 问题：
-- type: ALL（全表扫描，性能最差）
-- key: NULL（未使用索引）
-- rows: 全表行数
-- 原因：LIKE '%xxx' 左模糊匹配，索引失效


-- ============================================================
-- 3. 模拟慢查询示例
-- ============================================================

-- 模拟慢查询1：全表扫描
SELECT * FROM orders;  -- 无WHERE条件，全表扫描

-- 模拟慢查询2：未使用索引
SELECT * FROM products WHERE product_name LIKE '%可乐%';  -- 左模糊，索引失效

-- 模拟慢查询3：复杂JOIN查询
SELECT 
    o.order_no,
    o.total_amount,
    m.name as member_name,
    m.phone,
    u.nickname as cashier_name
FROM orders o
LEFT JOIN members m ON o.member_id = m.member_id
LEFT JOIN users u ON o.cashier_id = u.id
WHERE o.create_time >= '2025-01-01'
ORDER BY o.create_time DESC;
-- 建议：为关联字段添加索引


-- ============================================================
-- 4. 分析慢查询日志（Linux命令）
-- ============================================================

-- 查看慢查询日志文件
-- sudo cat /var/log/mysql/mysql-slow.log

-- 使用 mysqldumpslow 分析慢查询日志
-- mysqldumpslow -s t -t 10 /var/log/mysql/mysql-slow.log
-- 参数说明：
-- -s t：按查询时间排序
-- -t 10：显示前10条最慢的查询
-- -s c：按查询次数排序
-- -s l：按锁定时间排序
-- -s r：按返回记录数排序

-- 使用 pt-query-digest 分析慢查询日志（更强大）
-- pt-query-digest /var/log/mysql/mysql-slow.log > slow_report.txt


-- ============================================================
-- 5. 常见慢查询优化方案
-- ============================================================

-- 问题1：全表扫描（type: ALL）
-- 解决：为WHERE条件字段添加索引

-- 问题2：索引失效（key: NULL）
-- 原因：
--   - LIKE '%xxx' 左模糊匹配
--   - 对索引列使用函数：WHERE YEAR(create_time) = 2025
--   - 类型转换：WHERE member_id = 123（member_id是字符串）
-- 解决：
--   - LIKE改为右模糊：WHERE product_name LIKE '可乐%'
--   - 不使用函数：WHERE create_time >= '2025-01-01' AND create_time < '2026-01-01'
--   - 类型一致：WHERE member_id = '123'

-- 问题3：文件排序（Extra: Using filesort）
-- 原因：ORDER BY字段未使用索引
-- 解决：为ORDER BY字段添加索引

-- 问题4：临时表（Extra: Using temporary）
-- 原因：GROUP BY或DISTINCT未使用索引
-- 解决：为GROUP BY字段添加索引

-- 问题5：扫描行数过多（rows值很大）
-- 解决：优化WHERE条件，使用更精确的查询


-- ============================================================
-- 6. 索引优化建议
-- ============================================================

-- 建议1：避免过度索引
-- 索引过多会占用存储空间，降低写入性能
-- 建议：只为高频查询字段建索引

-- 建议2：组合索引遵循最左前缀原则
-- 组合索引 (a, b, c)：
-- WHERE a = ? ✓
-- WHERE a = ? AND b = ? ✓
-- WHERE b = ? ✗（未使用最左列a）

-- 建议3：定期维护索引
-- 删除无用索引：DROP INDEX idx_unused ON table_name;
-- 分析索引使用情况：SELECT * FROM sys.schema_index_statistics;

-- 建议4：使用覆盖索引
-- 覆盖索引：查询的所有字段都在索引中
-- 例如：SELECT member_id FROM members WHERE phone = '13800138000';
-- 如果有索引 idx_phone_memberid(phone, member_id)，则使用覆盖索引


-- ============================================================
-- 7. 查看表统计信息
-- ============================================================

-- 查看表大小
SELECT 
    TABLE_NAME,
    TABLE_ROWS,
    ROUND(DATA_LENGTH / 1024 / 1024, 2) AS 'Data Size (MB)',
    ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS 'Index Size (MB)'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'gbs_commerce_system_02'
ORDER BY TABLE_ROWS DESC;

-- 查看索引统计
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    CARDINALITY,  -- 索引基数（区分度）
    SEQ_IN_INDEX  -- 索引中的列序号
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'gbs_commerce_system_02'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;