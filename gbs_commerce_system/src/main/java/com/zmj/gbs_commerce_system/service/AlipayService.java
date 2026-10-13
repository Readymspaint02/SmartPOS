package com.zmj.gbs_commerce_system.service;

import java.util.Map;

public interface AlipayService {

    String createPayment(String orderNo, Double amount, String subject) throws Exception;

    Map<String, Object> queryPayment(String orderNo) throws Exception;

    Map<String, Object> queryLocalOrderStatus(String orderNo) throws Exception;

    void syncOrderStatus(String orderNo) throws Exception;

    boolean handleNotify(Map<String, String> params) throws Exception;

    boolean refund(String orderNo, Double refundAmount, String refundReason) throws Exception;

    Map<String, Object> queryRefund(String orderNo) throws Exception;
}