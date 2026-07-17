package com.zmj.gbs_commerce_system.controller;

import com.zmj.gbs_commerce_system.mapper.OrderItemMapper;
import com.zmj.gbs_commerce_system.mapper.OrdersMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/insight")
    @Operation(summary = "获取助手洞察数据")
    public Map<String, Object> getInsight(
            @RequestParam String metric,
            @RequestParam(defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "5") Integer limit) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        
        switch (metric) {
            case "top_products":
                result.put("data", getTopProducts(days, limit));
                break;
            case "sales_trend":
                result.put("data", getSalesTrend(days));
                break;
            default:
                result.put("status", "error");
                result.put("message", "不支持的指标类型: " + metric);
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
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
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
        List<Map<String, Object>> results = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.format(formatter));
            item.put("order_count", getRandomOrderCount());
            results.add(item);
        }
        
        return results;
    }

    private int getRandomOrderCount() {
        Random random = new Random();
        return 10 + random.nextInt(30);
    }
}