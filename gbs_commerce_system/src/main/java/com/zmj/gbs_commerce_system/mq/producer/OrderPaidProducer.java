package com.zmj.gbs_commerce_system.mq.producer;

import com.zmj.gbs_commerce_system.config.RabbitMQConfig;
import com.zmj.gbs_commerce_system.dto.OrderPaidMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class OrderPaidProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendOrderPaidMessage(Long orderId, String orderNo, BigDecimal paidAmount, Long cashierId) {
        OrderPaidMessage message = OrderPaidMessage.of(
                orderId,
                orderNo,
                null,
                paidAmount,
                paidAmount,
                cashierId,
                null
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_PAID_ROUTING_KEY,
                message
        );

        log.info("发送订单支付消息: orderId={}, orderNo={}", orderId, orderNo);
    }
}