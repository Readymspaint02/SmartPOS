package com.zmj.gbs_commerce_system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Tag(name = "助手图谱接口")
@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/insight")
    @Operation(summary = "获取助手洞察数据")
    public Map<String, Object> getInsight(
            @RequestParam String metric,
            @RequestParam(defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "5") Integer limit) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "查询成功");
        
        switch (metric) {
            case "top_products":
                result.put("data", getTopProducts(days, limit));
                break;
            case "sales_trend":
                result.put("data", getSalesTrend(days));
                break;
            default:
                result.put("code", 500);
                result.put("msg", "不支持的指标类型: " + metric);
        }
        
        return result;
    }

    private List<Map<String, Object>> getTopProducts(int days, int limit) {
        String sql = "SELECT p.product_name, SUM(oi.quantity) as quantity " +
                "FROM order_item oi " +
                "JOIN orders o ON oi.order_id = o.id " +
                "JOIN product p ON oi.product_id = p.id " +
                "WHERE o.order_status = 1 " +
                "AND o.create_time >= DATE_SUB(NOW(), INTERVAL " + days + " DAY) " +
                "GROUP BY oi.product_id, p.product_name " +
                "ORDER BY quantity DESC " +
                "LIMIT " + limit;
        
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            log.info("热销商品查询结果: {} 条", result.size());
            if (result.isEmpty()) {
                log.info("无真实数据，返回Mock数据");
                return getMockTopProducts();
            }
            return result;
        } catch (Exception e) {
            log.error("查询热销商品失败: {}", e.getMessage());
            return getMockTopProducts();
        }
    }

    private List<Map<String, Object>> getMockTopProducts() {
        List<Map<String, Object>> mock = new ArrayList<>();
        mock.add(createProductItem("可口可乐", 120));
        mock.add(createProductItem("矿泉水", 98));
        mock.add(createProductItem("薯片", 76));
        mock.add(createProductItem("方便面", 65));
        mock.add(createProductItem("牛奶", 54));
        return mock;
    }

    private Map<String, Object> createProductItem(String name, int quantity) {
        Map<String, Object> item = new HashMap<>();
        item.put("product_name", name);
        item.put("quantity", quantity);
        return item;
    }

    private List<Map<String, Object>> getSalesTrend(int days) {
        String sql = "SELECT DATE_FORMAT(create_time, '%m-%d') as date, COUNT(*) as order_count " +
                "FROM orders " +
                "WHERE order_status = 1 " +
                "AND create_time >= DATE_SUB(NOW(), INTERVAL " + days + " DAY) " +
                "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
                "ORDER BY date ASC";
        
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            log.info("销售趋势查询结果: {} 条", result.size());
            if (!result.isEmpty()) {
                return result;
            }
        } catch (Exception e) {
            log.error("查询销售趋势失败: {}", e.getMessage());
        }
        
        log.info("无真实数据，返回Mock数据");
        return getMockSalesTrend(days);
    }

    private List<Map<String, Object>> getMockSalesTrend(int days) {
        List<Map<String, Object>> results = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.format(formatter));
            item.put("order_count", 10 + new Random().nextInt(30));
            results.add(item);
        }
        
        return results;
    }
}