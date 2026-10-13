package com.zmj.gbs_commerce_system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    private String appId;
    private String appPrivateKey;
    private String platformPublicKey;
    private String gatewayUrl;
    private String notifyUrl;
    private String returnUrl;
    private String signType = "RSA2";
    private String charset = "UTF-8";
    private String format = "json";
    private Boolean sandbox = true;
}