package com.zmj.gbs_commerce_system.controller;

import com.zmj.gbs_commerce_system.annotation.RateLimit;
import com.zmj.gbs_commerce_system.dto.AsrRequest;
import com.zmj.gbs_commerce_system.metrics.BusinessMetrics;
import com.zmj.gbs_commerce_system.service.AsrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/asr")
@RequiredArgsConstructor
@Tag(name = "语音识别接口")
public class AsrController {

    private final AsrService asrService;
    private final BusinessMetrics businessMetrics;

    @Value("${huawei.asr.ak}")
    private String huaweiAk;

    @Value("${huawei.asr.sk}")
    private String huaweiSk;

    @Value("${huawei.asr.region:cn-north-4}")
    private String huaweiRegion;

    @Value("${huawei.asr.project-id}")
    private String huaweiProjectId;

    @PostMapping("/recognize")
    @Operation(summary = "一句话语音识别")
    @RateLimit(value = 20, windowSeconds = 60, limitType = RateLimit.LimitType.IP_AND_USER, message = "语音识别请求过于频繁，请稍后再试")
    public Map<String, Object> recognize(@RequestBody AsrRequest request) {
        businessMetrics.incrementAsrCall();
        
        try {
            String text = asrService.recognize(
                    request.getAudioBase64(),
                    request.getAudioFormat(),
                    request.getProperty()
            );
            businessMetrics.incrementAsrSuccess();
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("msg", "识别成功");
            result.put("data", text);
            return result;
        } catch (Exception e) {
            businessMetrics.incrementAsrFail();
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("msg", "识别失败：" + e.getMessage());
            return result;
        }
    }

    @PostMapping("/token")
    @Operation(summary = "获取华为云ASR签名信息（用于前端直连WebSocket流式识别）")
    @RateLimit(value = 10, windowSeconds = 60, limitType = RateLimit.LimitType.IP_AND_USER)
    public Map<String, Object> getToken() {
        try {
            String wsUrl = "wss://sis-ext." + huaweiRegion + ".myhuaweicloud.com/v1/" 
                    + huaweiProjectId + "/rasr/short-stream";
            
            Map<String, String> signHeaders = generateWsSignature(wsUrl);
            
            log.info("成功生成ASR签名信息");
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("wsUrl", wsUrl);
            result.put("projectId", huaweiProjectId);
            result.put("region", huaweiRegion);
            result.put("headers", signHeaders);
            return result;
        } catch (Exception e) {
            log.error("生成签名异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("msg", "生成签名失败: " + e.getMessage());
            return result;
        }
    }
    
    private Map<String, String> generateWsSignature(String wsUrl) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = sdf.format(new Date());
        
        SimpleDateFormat shortSdf = new SimpleDateFormat("yyyyMMdd");
        shortSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String shortDate = shortSdf.format(new Date());
        
        String host = "sis-ext." + huaweiRegion + ".myhuaweicloud.com";
        
        String canonicalRequest = "GET\n/v1/" + huaweiProjectId + "/rasr/short-stream\n\nhost:" + host + "\nx-sdk-date:" + date + "\n\nhost;x-sdk-date\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String hashedCanonicalRequest = HexFormat.of().formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        
        String stringToSign = "SDK-HMAC-SHA256\n" + date + "\n" + hashedCanonicalRequest;
        
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(huaweiSk.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        String signature = HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        
        String authorization = "SDK-HMAC-SHA256 Access=" + huaweiAk + ", SignedHeaders=host;x-sdk-date, Signature=" + signature;
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", authorization);
        headers.put("X-Sdk-Date", date);
        headers.put("Host", host);
        
        return headers;
    }
}