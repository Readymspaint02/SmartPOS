package com.zmj.gbs_commerce_system.controller;

import com.zmj.gbs_commerce_system.service.AlipayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alipay")
@Tag(name = "支付宝支付接口")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @PostMapping("/create")
    @Operation(summary = "创建支付宝支付订单")
    public Map<String, Object> createPayment(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String orderNo = (String) params.get("orderNo");
            Double amount = Double.valueOf(params.get("amount").toString());
            String subject = (String) params.getOrDefault("subject", "超市购物");

            String form = alipayService.createPayment(orderNo, amount, subject);
            result.put("code", 200);
            result.put("msg", "支付订单创建成功");
            result.put("data", form);
        } catch (Exception e) {
            log.error("创建支付宝支付失败", e);
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    @PostMapping("/notify")
    @Operation(summary = "支付宝支付回调（由支付宝调用）")
    public String handleNotify(HttpServletRequest request) {
        try {
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (String name : requestParams.keySet()) {
                String[] values = requestParams.get(name);
                StringBuilder valueStr = new StringBuilder();
                for (int i = 0; i < values.length; i++) {
                    valueStr.append((i == values.length - 1) ? values[i] : values[i] + ",");
                }
                params.put(name, valueStr.toString());
            }

            boolean success = alipayService.handleNotify(params);
            return success ? "success" : "fail";
        } catch (Exception e) {
            log.error("支付宝回调处理失败", e);
            return "fail";
        }
    }

    @GetMapping("/query/{orderNo}")
    @Operation(summary = "查询支付宝支付状态")
    public Map<String, Object> queryPayment(@PathVariable String orderNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = alipayService.queryPayment(orderNo);
            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", data);
        } catch (Exception e) {
            log.error("查询支付宝支付状态失败", e);
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    @GetMapping("/status/{orderNo}")
    @Operation(summary = "查询本地订单支付状态（轻量级轮询）")
    public Map<String, Object> queryLocalStatus(@PathVariable String orderNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = alipayService.queryLocalOrderStatus(orderNo);
            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", data);
        } catch (Exception e) {
            log.error("查询订单状态失败", e);
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    @PostMapping("/sync/{orderNo}")
    @Operation(summary = "同步支付宝订单状态（手动触发）")
    public Map<String, Object> syncPaymentStatus(@PathVariable String orderNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            alipayService.syncOrderStatus(orderNo);
            Map<String, Object> data = alipayService.queryLocalOrderStatus(orderNo);
            result.put("code", 200);
            result.put("msg", "同步成功");
            result.put("data", data);
        } catch (Exception e) {
            log.error("同步订单状态失败", e);
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    @PostMapping("/refund")
    @Operation(summary = "支付宝退款")
    public Map<String, Object> refund(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String orderNo = (String) params.get("orderNo");
            Double refundAmount = Double.valueOf(params.get("refundAmount").toString());
            String refundReason = (String) params.getOrDefault("refundReason", "用户申请退款");

            boolean success = alipayService.refund(orderNo, refundAmount, refundReason);
            result.put("code", success ? 200 : 500);
            result.put("msg", success ? "退款成功" : "退款失败");
        } catch (Exception e) {
            log.error("支付宝退款失败", e);
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    @GetMapping("/refund/query/{orderNo}")
    @Operation(summary = "查询支付宝退款状态")
    public Map<String, Object> queryRefund(@PathVariable String orderNo) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = alipayService.queryRefund(orderNo);
            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", data);
        } catch (Exception e) {
            log.error("查询支付宝退款状态失败", e);
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }
}