package com.zmj.gbs_commerce_system.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class PaymentRequest {
    private List<Map<String, Object>> cartItems;
    private BigDecimal paidAmount;
    private BigDecimal discountAmount;
    private String memberId;
    private String remark;
}