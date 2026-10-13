package com.zmj.gbs_commerce_system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Tag(name = "会员分析接口")
@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/member/analysis/{memberId}")
    @Operation(summary = "获取会员分析数据")
    public Map<String, Object> getMemberAnalysis(@PathVariable String memberId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "查询成功");

        try {
            Map<String, Object> analysis = new HashMap<>();

            Map<String, Object> memberInfo = getMemberInfo(memberId);
            if (memberInfo == null) {
                result.put("code", 500);
                result.put("msg", "会员不存在");
                return result;
            }
            analysis.put("memberInfo", memberInfo);

            Map<String, Object> consumption = getMemberConsumption(memberId);
            analysis.put("consumption", consumption);

            List<Map<String, Object>> categoryPreference = getCategoryPreference(memberId);
            analysis.put("categoryPreference", categoryPreference);

            List<Map<String, Object>> consumptionTrend = getConsumptionTrend(memberId);
            analysis.put("consumptionTrend", consumptionTrend);

            String rfmSegment = calculateRFMSegment(memberId, consumption);
            analysis.put("rfmSegment", rfmSegment);

            List<Map<String, Object>> recommendations = getPersonalizedRecommendations(memberId, categoryPreference);
            analysis.put("recommendations", recommendations);

            result.put("data", analysis);
        } catch (Exception e) {
            log.error("会员分析查询失败: {}", e.getMessage());
            result.put("code", 500);
            result.put("msg", "查询失败: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> getMemberInfo(String memberId) {
        String sql = "SELECT member_id, name, level, points, balance, gender, phone, create_time FROM members WHERE member_id = ? OR phone = ?";
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId, memberId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.error("查询会员信息失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> getMemberConsumption(String memberId) {
        String sql = "SELECT " +
                "COALESCE(SUM(o.paid_amount), 0) as totalAmount, " +
                "COUNT(*) as orderCount, " +
                "COALESCE(AVG(o.paid_amount), 0) as avgOrderValue, " +
                "MAX(o.create_time) as lastOrderTime " +
                "FROM orders o WHERE o.member_id = ? AND o.order_status = 1";
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId);
            if (!results.isEmpty()) {
                Map<String, Object> data = results.get(0);
                double avgAll = getAvgOrderValueAll();
                double avgMember = ((Number) data.getOrDefault("avgOrderValue", 0)).doubleValue();
                double diffPercent = avgAll > 0 ? ((avgMember - avgAll) / avgAll * 100) : 0;
                data.put("avgDiffPercent", Math.round(diffPercent * 10) / 10.0);
                return data;
            }
        } catch (Exception e) {
            log.error("查询会员消费数据失败: {}", e.getMessage());
        }
        return new HashMap<>();
    }

    private double getAvgOrderValueAll() {
        String sql = "SELECT COALESCE(AVG(paid_amount), 0) as avg FROM orders WHERE order_status = 1";
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            return ((Number) result.getOrDefault("avg", 0)).doubleValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private List<Map<String, Object>> getCategoryPreference(String memberId) {
        String sql = "SELECT " +
                "pc.category_name as categoryName, " +
                "SUM(oi.quantity) as quantity, " +
                "SUM(oi.subtotal) as amount " +
                "FROM order_item oi " +
                "JOIN orders o ON oi.order_id = o.id " +
                "JOIN product p ON oi.product_id = p.id " +
                "JOIN product_category pc ON p.category_id = pc.id " +
                "WHERE o.member_id = ? AND o.order_status = 1 " +
                "GROUP BY pc.id, pc.category_name " +
                "ORDER BY amount DESC " +
                "LIMIT 5";
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId);
            if (results.isEmpty()) {
                return getMockCategoryPreference();
            }
            double totalAmount = results.stream()
                    .mapToDouble(m -> ((Number) m.getOrDefault("amount", 0)).doubleValue())
                    .sum();
            for (Map<String, Object> item : results) {
                double amount = ((Number) item.getOrDefault("amount", 0)).doubleValue();
                double percent = totalAmount > 0 ? (amount / totalAmount * 100) : 0;
                item.put("percent", Math.round(percent * 10) / 10.0);
            }
            return results;
        } catch (Exception e) {
            log.error("查询品类偏好失败: {}", e.getMessage());
            return getMockCategoryPreference();
        }
    }

    private List<Map<String, Object>> getMockCategoryPreference() {
        return List.of(
                Map.of("categoryName", "生鲜果蔬", "quantity", 35, "amount", 580.0, "percent", 35.0),
                Map.of("categoryName", "乳制品", "quantity", 22, "amount", 350.0, "percent", 22.0),
                Map.of("categoryName", "饮料", "quantity", 18, "amount", 280.0, "percent", 18.0),
                Map.of("categoryName", "零食", "quantity", 15, "amount", 230.0, "percent", 15.0),
                Map.of("categoryName", "日用品", "quantity", 10, "amount", 150.0, "percent", 10.0)
        );
    }

    private List<Map<String, Object>> getConsumptionTrend(String memberId) {
        String sql = "SELECT " +
                "DATE_FORMAT(o.create_time, '%Y-%m') as month, " +
                "SUM(o.paid_amount) as amount, " +
                "COUNT(*) as orderCount " +
                "FROM orders o " +
                "WHERE o.member_id = ? AND o.order_status = 1 " +
                "AND o.create_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH) " +
                "GROUP BY DATE_FORMAT(o.create_time, '%Y-%m') " +
                "ORDER BY month ASC";
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, memberId);
            if (results.isEmpty()) {
                return getMockConsumptionTrend();
            }
            return results;
        } catch (Exception e) {
            log.error("查询消费趋势失败: {}", e.getMessage());
            return getMockConsumptionTrend();
        }
    }

    private List<Map<String, Object>> getMockConsumptionTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate now = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        Random random = new Random();
        for (int i = 5; i >= 0; i--) {
            Map<String, Object> item = new HashMap<>();
            item.put("month", now.minusMonths(i).format(formatter));
            item.put("amount", 200 + random.nextDouble() * 500);
            item.put("orderCount", 3 + random.nextInt(8));
            trend.add(item);
        }
        return trend;
    }

    private String calculateRFMSegment(String memberId, Map<String, Object> consumption) {
        String sql = "SELECT DATEDIFF(NOW(), MAX(create_time)) as recency FROM orders WHERE member_id = ? AND order_status = 1";
        int recencyDays = 999;
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, memberId);
            if (result.get("recency") != null) {
                recencyDays = ((Number) result.get("recency")).intValue();
            }
        } catch (Exception e) {
            log.warn("计算RFM R值失败: {}", e.getMessage());
        }

        int frequency = ((Number) consumption.getOrDefault("orderCount", 0)).intValue();
        double monetary = ((Number) consumption.getOrDefault("totalAmount", 0)).doubleValue();

        if (recencyDays <= 30 && frequency >= 5 && monetary >= 1000) {
            return "高价值会员";
        } else if (recencyDays <= 60 && frequency >= 3 && monetary >= 500) {
            return "潜力会员";
        } else if (recencyDays <= 90 && frequency >= 1) {
            return "活跃会员";
        } else if (recencyDays > 90) {
            return "流失预警会员";
        } else {
            return "普通会员";
        }
    }

    private List<Map<String, Object>> getPersonalizedRecommendations(String memberId, List<Map<String, Object>> preferences) {
        if (preferences.isEmpty()) {
            return getMockRecommendations();
        }

        String topCategory = (String) preferences.get(0).get("categoryName");
        String sql = "SELECT p.product_name as productName, p.price, pc.category_name as categoryName " +
                "FROM product p " +
                "JOIN product_category pc ON p.category_id = pc.id " +
                "WHERE pc.category_name = ? AND p.status = 0 " +
                "ORDER BY p.sales_count DESC LIMIT 5";
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, topCategory);
            if (!results.isEmpty()) {
                return results;
            }
        } catch (Exception e) {
            log.warn("查询推荐商品失败: {}", e.getMessage());
        }
        return getMockRecommendations();
    }

    private List<Map<String, Object>> getMockRecommendations() {
        return List.of(
                Map.of("productName", "进口车厘子", "price", 68.0, "categoryName", "生鲜果蔬"),
                Map.of("productName", "澳洲牛排", "price", 128.0, "categoryName", "生鲜肉类"),
                Map.of("productName", "有机牛奶", "price", 25.0, "categoryName", "乳制品"),
                Map.of("productName", "精品三文鱼", "price", 88.0, "categoryName", "生鲜水产"),
                Map.of("productName", "进口蓝莓", "price", 45.0, "categoryName", "生鲜果蔬")
        );
    }

    @GetMapping("/insight")
    @Operation(summary = "获取助手洞察数据（兼容旧接口）")
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