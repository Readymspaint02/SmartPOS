package com.zmj.gbs_commerce_system.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.zmj.gbs_commerce_system.config.AlipayProperties;
import com.zmj.gbs_commerce_system.entity.OrderItem;
import com.zmj.gbs_commerce_system.entity.Orders;
import com.zmj.gbs_commerce_system.mapper.OrderItemMapper;
import com.zmj.gbs_commerce_system.mapper.OrdersMapper;
import com.zmj.gbs_commerce_system.service.AlipayService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {

    @Autowired
    private AlipayProperties alipayProperties;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private AlipayClient alipayClient;

    private AlipayClient getAlipayClient() {
        if (alipayClient == null) {
            alipayClient = new DefaultAlipayClient(
                    alipayProperties.getGatewayUrl(),
                    alipayProperties.getAppId(),
                    alipayProperties.getAppPrivateKey(),
                    alipayProperties.getFormat(),
                    alipayProperties.getCharset(),
                    alipayProperties.getPlatformPublicKey(),
                    alipayProperties.getSignType()
            );
        }
        return alipayClient;
    }

    @Override
    public String createPayment(String orderNo, Double amount, String subject) throws Exception {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(alipayProperties.getNotifyUrl());

        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(orderNo);
        model.setTotalAmount(String.format("%.2f", amount));
        model.setSubject(subject);
        request.setBizModel(model);

        try {
            AlipayTradePrecreateResponse response = getAlipayClient().execute(request);
            if (response.isSuccess()) {
                log.info("支付宝当面付二维码创建成功: orderNo={}, qrCode={}", orderNo, response.getQrCode());
                String cacheKey = "alipay:pending:" + orderNo;
                redisTemplate.opsForValue().set(cacheKey, amount, 30, TimeUnit.MINUTES);
                return response.getQrCode();
            } else {
                log.error("支付宝当面付创建失败: code={}, msg={}", response.getCode(), response.getMsg());
                throw new RuntimeException("支付宝支付创建失败: " + response.getMsg());
            }
        } catch (AlipayApiException e) {
            log.error("支付宝API调用异常: {}", e.getMessage(), e);
            throw new RuntimeException("支付宝支付创建失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryPayment(String orderNo) throws Exception {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(orderNo);
        request.setBizModel(model);

        try {
            AlipayTradeQueryResponse response = getAlipayClient().execute(request);
            Map<String, Object> result = new HashMap<>();
            if (response.isSuccess()) {
                result.put("tradeNo", response.getTradeNo());
                result.put("outTradeNo", response.getOutTradeNo());
                result.put("tradeStatus", response.getTradeStatus());
                result.put("totalAmount", response.getTotalAmount());
                result.put("buyerLogonId", response.getBuyerLogonId());
                result.put("success", true);
                log.info("支付宝订单查询成功: orderNo={}, status={}", orderNo, response.getTradeStatus());
            } else {
                result.put("success", false);
                result.put("code", response.getCode());
                result.put("msg", response.getMsg());
                log.warn("支付宝订单查询失败: code={}, msg={}", response.getCode(), response.getMsg());
            }
            return result;
        } catch (AlipayApiException e) {
            log.error("支付宝订单查询异常: {}", e.getMessage(), e);
            throw new RuntimeException("支付宝订单查询失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryLocalOrderStatus(String orderNo) {
        Map<String, Object> result = new HashMap<>();
        Orders order = ordersMapper.selectByOrderNo(orderNo);
        if (order == null) {
            result.put("success", false);
            result.put("msg", "订单不存在");
            return result;
        }
        result.put("orderNo", order.getOrderNo());
        result.put("orderStatus", order.getOrderStatus());
        result.put("paidAmount", order.getPaidAmount());
        result.put("paymentMethod", order.getPaymentMethod());
        result.put("success", true);
        result.put("tradeStatus", order.getOrderStatus() == 1 ? "TRADE_SUCCESS" : "WAIT_BUYER_PAY");
        return result;
    }

    @Override
    @Transactional
    public void syncOrderStatus(String orderNo) throws Exception {
        Orders order = ordersMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getOrderStatus() == 1) {
            log.info("订单已支付: orderNo={}", orderNo);
            return;
        }

        try {
            Map<String, Object> queryResult = queryPayment(orderNo);
            log.info("支付宝查询结果: {}", queryResult);
            
            if (queryResult.containsKey("tradeStatus")) {
                String tradeStatus = (String) queryResult.get("tradeStatus");
                log.info("交易状态: {}", tradeStatus);
                
                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                    deductInventory(orderNo);
                    order.setOrderStatus(1);
                    ordersMapper.updateById(order);
                    log.info("同步订单状态成功: orderNo={}", orderNo);
                } else {
                    log.info("交易状态未成功: {}", tradeStatus);
                }
            } else {
                log.warn("查询结果无tradeStatus: {}", queryResult);
            }
        } catch (Exception e) {
            log.error("同步订单状态失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void deductInventory(String orderNo) {
        String sql = "SELECT oi.product_id, oi.quantity FROM order_item oi " +
                "JOIN orders o ON oi.order_id = o.id WHERE o.order_no = ?";
        try {
            var items = jdbcTemplate.queryForList(sql, orderNo);
            for (var item : items) {
                Long productId = ((Number) item.get("product_id")).longValue();
                Integer quantity = ((Number) item.get("quantity")).intValue();
                
                String updateSql = "UPDATE inventory SET stock_quantity = stock_quantity - ? WHERE product_id = ?";
                int rows = jdbcTemplate.update(updateSql, quantity, productId);
                if (rows > 0) {
                    log.info("库存扣减成功: productId={}, quantity={}", productId, quantity);
                } else {
                    log.warn("库存扣减失败: productId={}", productId);
                }
            }
        } catch (Exception e) {
            log.error("扣减库存失败: {}", e.getMessage(), e);
            throw new RuntimeException("扣减库存失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean handleNotify(Map<String, String> params) throws Exception {
        boolean signVerified = AlipaySignature.rsaCheckV1(
                params,
                alipayProperties.getPlatformPublicKey(),
                alipayProperties.getCharset(),
                alipayProperties.getSignType()
        );

        if (!signVerified) {
            log.error("支付宝回调签名验证失败");
            return false;
        }

        String tradeStatus = params.get("trade_status");
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");

        log.info("支付宝回调: orderNo={}, tradeNo={}, status={}, amount={}", 
                outTradeNo, tradeNo, tradeStatus, totalAmount);

        String lockKey = "lock:alipay:notify:" + outTradeNo;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取分布式锁失败，可能正在处理中: orderNo={}", outTradeNo);
                return true;
            }

            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                Orders order = ordersMapper.selectByOrderNo(outTradeNo);
                if (order == null) {
                    log.error("订单不存在: orderNo={}", outTradeNo);
                    return false;
                }

                if (order.getOrderStatus() == 1) {
                    log.info("订单已支付，跳过处理: orderNo={}", outTradeNo);
                    return true;
                }

                deductInventory(outTradeNo);

                order.setOrderStatus(1);
                order.setPaymentMethod(3);
                ordersMapper.updateById(order);

                String cacheKey = "alipay:pending:" + outTradeNo;
                redisTemplate.delete(cacheKey);

                log.info("支付宝支付成功，订单状态已更新: orderNo={}", outTradeNo);
            } else {
                log.info("支付宝交易状态未成功: status={}", tradeStatus);
            }

            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public boolean refund(String orderNo, Double refundAmount, String refundReason) throws Exception {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(orderNo);
        model.setRefundAmount(String.format("%.2f", refundAmount));
        model.setRefundReason(refundReason);
        request.setBizModel(model);

        try {
            AlipayTradeRefundResponse response = getAlipayClient().execute(request);
            if (response.isSuccess()) {
                log.info("支付宝退款成功: orderNo={}, refundAmount={}", orderNo, refundAmount);
                
                Orders order = ordersMapper.selectByOrderNo(orderNo);
                if (order != null) {
                    order.setOrderStatus(2);
                    ordersMapper.updateById(order);
                }
                return true;
            } else {
                log.error("支付宝退款失败: code={}, msg={}", response.getCode(), response.getMsg());
                return false;
            }
        } catch (AlipayApiException e) {
            log.error("支付宝退款异常: {}", e.getMessage(), e);
            throw new RuntimeException("支付宝退款失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryRefund(String orderNo) throws Exception {
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(orderNo);
        model.setOutRequestNo(orderNo);
        request.setBizModel(model);

        try {
            AlipayTradeFastpayRefundQueryResponse response = getAlipayClient().execute(request);
            Map<String, Object> result = new HashMap<>();
            if (response.isSuccess()) {
                result.put("refundStatus", response.getRefundStatus());
                result.put("refundAmount", response.getRefundAmount());
                result.put("success", true);
                log.info("支付宝退款查询成功: orderNo={}", orderNo);
            } else {
                result.put("success", false);
                result.put("code", response.getCode());
                result.put("msg", response.getMsg());
            }
            return result;
        } catch (AlipayApiException e) {
            log.error("支付宝退款查询异常: {}", e.getMessage(), e);
            throw new RuntimeException("支付宝退款查询失败: " + e.getMessage());
        }
    }
}