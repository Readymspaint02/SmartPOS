-- ============================================================
-- 【MySQL-索引优化】数据库索引创建脚本
-- ============================================================
-- 
-- 文件作用：
-- 为数据库表创建索引，提升查询性能。
-- 索引类型：主键索引、唯一索引、普通索引、组合索引。
-- 
-- 面试考点：
-- - Q1：什么情况下应该建索引？
--   A1：1. WHERE 条件字段
--       2. ORDER BY 排序字段
--       3. JOIN 关联字段
--       4. 区分度高的字段（性别不适合）
-- 
-- - Q2：什么情况下索引会失效？
--   A2：1. 使用 LIKE '%xxx' 左模糊匹配
--       2. 对索引列使用函数或计算
--       3. OR 条件中有一列没有索引
--       4. 组合索引未使用最左列
--       5. 类型转换（字符串字段用数字查询）
-- 
-- - Q3：组合索引的最左前缀原则？
--   A3：组合索引 (a, b, c)：
--       - WHERE a = ? 生效
--       - WHERE a = ? AND b = ? 生效
--       - WHERE b = ? 不生效（未使用最左列a）
-- 
-- - Q4：索引的优缺点？
--   A4：优点：加快查询速度
--       缺点：占用存储空间、降低INSERT/UPDATE/DELETE速度
--       建议：查询多的表多建索引，写入多的表少建索引
-- 
-- 参考文档：
-- - 简历-八股文-必备.txt 4.1 MySQL索引优化
-- ============================================================

USE gbs_commerce_system_02;

-- ============================================================
-- 1. 用户表（users）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)

-- 唯一索引：用户名唯一（用于登录查询）
CREATE UNIQUE INDEX IF NOT EXISTS `uniq_username` ON `users`(`username`);

-- 普通索引：状态字段（用于筛选启用的用户）
CREATE INDEX IF NOT EXISTS `idx_status` ON `users`(`status`);

-- 组合索引：状态+创建时间（用于分页查询启用的用户列表）
CREATE INDEX IF NOT EXISTS `idx_status_createtime` ON `users`(`status`, `create_time`);


-- ============================================================
-- 2. 商品表（products）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)

-- 唯一索引：商品编码唯一（用于扫码查询）
CREATE UNIQUE INDEX IF NOT EXISTS `uniq_product_code` ON `products`(`product_code`);

-- 普通索引：分类ID（用于按分类查询商品）
CREATE INDEX IF NOT EXISTS `idx_category_id` ON `products`(`category_id`);

-- 普通索引：状态字段（用于筛选上架商品）
CREATE INDEX IF NOT EXISTS `idx_status` ON `products`(`status`);

-- 组合索引：分类+状态（用于按分类查询上架商品）
CREATE INDEX IF NOT EXISTS `idx_category_status` ON `products`(`category_id`, `status`);

-- 组合索引：状态+创建时间（用于分页查询上架商品列表）
CREATE INDEX IF NOT EXISTS `idx_status_createtime` ON `products`(`status`, `create_time`);


-- ============================================================
-- 3. 订单表（orders）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)

-- 唯一索引：订单号唯一（用于查询订单详情）
CREATE UNIQUE INDEX IF NOT EXISTS `uniq_order_no` ON `orders`(`order_no`);

-- 普通索引：会员ID（用于查询会员的订单历史）
CREATE INDEX IF NOT EXISTS `idx_member_id` ON `orders`(`member_id`);

-- 普通索引：收银员ID（用于查询收银员的订单记录）
CREATE INDEX IF NOT EXISTS `idx_cashier_id` ON `orders`(`cashier_id`);

-- 普通索引：订单状态（用于筛选订单状态）
CREATE INDEX IF NOT EXISTS `idx_order_status` ON `orders`(`order_status`);

-- 普通索引：支付方式（用于统计支付方式分布）
CREATE INDEX IF NOT EXISTS `idx_payment_method` ON `orders`(`payment_method`);

-- 组合索引：状态+创建时间（用于分页查询不同状态的订单）
CREATE INDEX IF NOT EXISTS `idx_status_createtime` ON `orders`(`order_status`, `create_time`);

-- 组合索引：会员+状态+创建时间（用于查询会员的订单历史）
CREATE INDEX IF NOT EXISTS `idx_member_status_createtime` ON `orders`(`member_id`, `order_status`, `create_time`);


-- ============================================================
-- 4. 订单项表（order_items）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)

-- 普通索引：订单ID（用于查询订单的商品明细）
CREATE INDEX IF NOT EXISTS `idx_order_id` ON `order_items`(`order_id`);

-- 普通索引：商品ID（用于统计商品销量）
CREATE INDEX IF NOT EXISTS `idx_product_id` ON `order_items`(`product_id`);


-- ============================================================
-- 5. 会员表（members）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)

-- 唯一索引：会员编号唯一（用于查询会员信息）
CREATE UNIQUE INDEX IF NOT EXISTS `uniq_member_id` ON `members`(`member_id`);

-- 普通索引：手机号（用于手机号查询会员）
CREATE INDEX IF NOT EXISTS `idx_phone` ON `members`(`phone`);

-- 普通索引：等级字段（用于筛选会员等级）
CREATE INDEX IF NOT EXISTS `idx_level` ON `members`(`level`);

-- 普通索引：状态字段（用于筛选正常/禁用会员）
CREATE INDEX IF NOT EXISTS `idx_status` ON `members`(`status`);

-- 组合索引：状态+创建时间（用于分页查询正常会员列表）
CREATE INDEX IF NOT EXISTS `idx_status_createtime` ON `members`(`status`, `create_time`);


-- ============================================================
-- 6. 库存表（inventory）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)
-- 已有唯一索引：UNIQUE INDEX `uniq_product_id`(`product_id`)


-- ============================================================
-- 7. 库存日志表（inventory_log）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)
-- 已有索引：INDEX `idx_product_id`(`product_id`)
-- 已有索引：INDEX `idx_create_time`(`create_time`)


-- ============================================================
-- 8. 角色表（roles）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)

-- 唯一索引：角色名称唯一
CREATE UNIQUE INDEX IF NOT EXISTS `uniq_role_name` ON `roles`(`role_name`);


-- ============================================================
-- 9. 菜单表（menus）索引优化
-- ============================================================

-- 已有主键索引：PRIMARY KEY (`id`)

-- 普通索引：父菜单ID（用于查询子菜单）
CREATE INDEX IF NOT EXISTS `idx_parent_id` ON `menus`(`parent_id`);

-- 普通索引：排序字段（用于菜单排序）
CREATE INDEX IF NOT EXISTS `idx_order_num` ON `menus`(`order_num`);


-- ============================================================
-- 10. 查看索引创建情况
-- ============================================================

-- 查看表的索引
SHOW INDEX FROM users;
SHOW INDEX FROM products;
SHOW INDEX FROM orders;
SHOW INDEX FROM order_items;
SHOW INDEX FROM members;

-- 查看表的索引大小
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    ROUND(STAT_VALUE * @@innodb_page_size / 1024 / 1024, 2) AS 'Size (MB)'
FROM mysql.innodb_index_stats
WHERE DATABASE_NAME = 'gbs_commerce_system_02'
    AND STAT_NAME = 'size'
ORDER BY STAT_VALUE DESC
LIMIT 20;
