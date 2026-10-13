package com.zmj.gbs_commerce_system.service.impl;

import com.zmj.gbs_commerce_system.mapper.ReportMapper;
import com.zmj.gbs_commerce_system.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String CACHE_PREFIX = "report:";
    private static final long CACHE_TTL = 30;

    @Override
    public Map<String, Object> getSalesOverview() {
        String cacheKey = CACHE_PREFIX + "salesOverview";
        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> overview = new HashMap<>(reportMapper.getSalesOverview());
        overview.put("topProducts", reportMapper.getTopProducts());
        Integer memberCount = reportMapper.getMemberCount();
        overview.put("totalMembers", memberCount == null ? 0 : memberCount);
        redisTemplate.opsForValue().set(cacheKey, overview, CACHE_TTL, TimeUnit.MINUTES);
        return overview;
    }

    @Override
    public Map<String, Object> getTodaySalesOverview() {
        Map<String, Object> today = reportMapper.getTodaySalesOverview();
        if (today == null) {
            today = new HashMap<>();
            today.put("todaySales", 0);
            today.put("todayOrders", 0);
            today.put("todayAvgOrderValue", 0);
        }
        Integer newMembers = reportMapper.getNewMembersToday();
        today.put("newMembersToday", newMembers == null ? 0 : newMembers);
        
        Map<String, Object> yesterday = getYesterdaySales();
        long todaySales = ((Number) today.getOrDefault("todaySales", 0)).longValue();
        long yesterdaySales = ((Number) yesterday.getOrDefault("yesterdaySales", 0)).longValue();
        double salesGrowth = yesterdaySales > 0 ? ((todaySales - yesterdaySales) * 100.0 / yesterdaySales) : 0;
        today.put("salesGrowth", Math.round(salesGrowth * 10) / 10.0);
        
        long todayOrders = ((Number) today.getOrDefault("todayOrders", 0)).longValue();
        long yesterdayOrders = ((Number) yesterday.getOrDefault("yesterdayOrders", 0)).longValue();
        double ordersGrowth = yesterdayOrders > 0 ? ((todayOrders - yesterdayOrders) * 100.0 / yesterdayOrders) : 0;
        today.put("ordersGrowth", Math.round(ordersGrowth * 10) / 10.0);
        
        return today;
    }

    private Map<String, Object> getYesterdaySales() {
        String sql = "SELECT COALESCE(SUM(paid_amount),0) AS yesterdaySales, COUNT(*) AS yesterdayOrders " +
                     "FROM orders WHERE DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)";
        return reportMapper.getYesterdaySales();
    }

    @Override
    public List<Map<String, Object>> getRecentOrders(int limit) {
        return reportMapper.getRecentOrders(limit);
    }

    @Override
    public Integer getNewMembersToday() {
        Integer count = reportMapper.getNewMembersToday();
        return count == null ? 0 : count;
    }

    @Override
    public List<Map<String, Object>> getSalesTrend(int days) {
        int range = Math.max(1, Math.min(days, 30));
        List<Map<String, Object>> raw = reportMapper.getSalesTrendRaw(range - 1);
        Map<String, Map<String, Object>> rawMap = new HashMap<>();
        if (raw != null) {
            for (Map<String, Object> entry : raw) {
                rawMap.put(String.valueOf(entry.get("day")), entry);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = range - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.format(DATE_FORMATTER);
            Map<String, Object> data = rawMap.getOrDefault(key, null);
            Map<String, Object> row = new HashMap<>();
            row.put("day", key);
            row.put("totalSales", data == null ? 0 : data.get("totalSales"));
            row.put("orderCount", data == null ? 0 : data.get("orderCount"));
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getInventoryWarnings() {
        return reportMapper.getInventoryWarnings();
    }

    @Override
    public Map<String, Object> getInventoryStats() {
        return reportMapper.getInventoryStats();
    }
}
