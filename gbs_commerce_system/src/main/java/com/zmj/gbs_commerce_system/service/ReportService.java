package com.zmj.gbs_commerce_system.service;

import java.util.List;
import java.util.Map;

public interface ReportService {

    Map<String, Object> getSalesOverview();

    Map<String, Object> getTodaySalesOverview();

    List<Map<String, Object>> getSalesTrend(int days);

    List<Map<String, Object>> getInventoryWarnings();

    List<Map<String, Object>> getRecentOrders(int limit);

    Integer getNewMembersToday();

    Map<String, Object> getInventoryStats();
}
