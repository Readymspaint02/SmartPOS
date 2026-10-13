package com.zmj.gbs_commerce_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {

    @Select("""
            SELECT
                COALESCE(SUM(paid_amount),0) AS totalSales,
                COUNT(*) AS totalOrders,
                COALESCE(AVG(paid_amount),0) AS avgOrderValue
            FROM orders
            """)
    Map<String, Object> getSalesOverview();

    @Select("""
            SELECT
                DATE_FORMAT(create_time,'%Y-%m-%d') AS day,
                COALESCE(SUM(paid_amount),0) AS totalSales,
                COUNT(*) AS orderCount
            FROM orders
            WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{intervalDays} DAY)
            GROUP BY DATE_FORMAT(create_time,'%Y-%m-%d')
            ORDER BY day ASC
            """)
    List<Map<String, Object>> getSalesTrendRaw(@Param("intervalDays") int intervalDays);

    @Select("""
            SELECT
                oi.product_id AS productId,
                oi.product_name AS productName,
                SUM(oi.quantity) AS totalQuantity,
                SUM(oi.subtotal) AS totalAmount
            FROM order_item oi
            GROUP BY oi.product_id, oi.product_name
            ORDER BY totalAmount DESC
            LIMIT 5
            """)
    List<Map<String, Object>> getTopProducts();

    @Select("""
            SELECT COUNT(*) FROM members
            """)
    Integer getMemberCount();

    @Select("""
            SELECT
                COALESCE(SUM(paid_amount),0) AS todaySales,
                COUNT(*) AS todayOrders,
                COALESCE(AVG(paid_amount),0) AS todayAvgOrderValue
            FROM orders
            WHERE DATE(create_time) = CURDATE()
            """)
    Map<String, Object> getTodaySalesOverview();

    @Select("""
            SELECT
                o.id,
                o.order_no AS orderNo,
                o.paid_amount AS paidAmount,
                o.payment_method AS paymentMethod,
                o.create_time AS createTime,
                o.member_id AS memberId,
                m.name AS memberName
            FROM orders o
            LEFT JOIN members m ON o.member_id = m.member_id
            WHERE o.order_status = 1
            ORDER BY o.create_time DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> getRecentOrders(@Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS newMembersToday
            FROM members
            WHERE DATE(create_time) = CURDATE()
            """)
    Integer getNewMembersToday();

    @Select("""
            SELECT
                COALESCE(SUM(paid_amount),0) AS yesterdaySales,
                COUNT(*) AS yesterdayOrders
            FROM orders
            WHERE DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
            """)
    Map<String, Object> getYesterdaySales();

    @Select("""
            SELECT
                i.id,
                i.product_id AS productId,
                p.product_name AS productName,
                p.product_code AS productCode,
                i.stock_quantity AS stockQuantity,
                i.warning_quantity AS warningQuantity,
                (i.stock_quantity - i.warning_quantity) AS diff
            FROM inventory i
            INNER JOIN product p ON p.id = i.product_id
            WHERE p.product_name IS NOT NULL AND p.product_name != ''
              AND p.status = 0
              AND (i.stock_quantity <= i.warning_quantity OR i.stock_quantity <= 10)
            ORDER BY i.stock_quantity ASC
            """)
    List<Map<String, Object>> getInventoryWarnings();

    @Select("""
            SELECT
                COUNT(DISTINCT p.id) AS totalProducts,
                (SELECT COUNT(*) FROM inventory i INNER JOIN product p ON p.id = i.product_id WHERE p.status = 0 AND i.stock_quantity <= i.warning_quantity) AS warningCount,
                (SELECT COUNT(*) FROM inventory i INNER JOIN product p ON p.id = i.product_id WHERE p.status = 0 AND i.stock_quantity > 0 AND i.stock_quantity <= 10) AS lowStockCount
            FROM product p
            WHERE p.status = 0
            """)
    Map<String, Object> getInventoryStats();
}
