-- 修复菜单路径问题
-- 问题：子菜单路径缺少父级路径前缀，导致路由匹配失败

-- 1. 商品列表：/list -> /product/list
UPDATE sys_menu SET path = '/product/list' WHERE id = 202;

-- 2. 商品入库：/in -> /inventory/in
UPDATE sys_menu SET path = '/inventory/in' WHERE id = 301;

-- 3. 商品出库：/out -> /inventory/out
UPDATE sys_menu SET path = '/inventory/out' WHERE id = 302;

-- 4. 订单查询：/order -> /cashier/order
UPDATE sys_menu SET path = '/cashier/order' WHERE id = 402;

-- 验证修改
SELECT id, menu_name, path, component FROM sys_menu WHERE id IN (202, 301, 302, 402);