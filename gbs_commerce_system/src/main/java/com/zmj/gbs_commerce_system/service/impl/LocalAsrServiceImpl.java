package com.zmj.gbs_commerce_system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmj.gbs_commerce_system.service.AsrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地语音识别实现（策略模式 local 分支）。
 * 依赖本地部署的 ASR 推理服务（如 Vosk / faster-whisper 封装的 REST 服务），
 * 约定协议：
 *   POST {endpoint}/recognize {"audio_base64","audio_format"} -> {"text":str}
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "asr.provider", havingValue = "local")
public class LocalAsrServiceImpl implements AsrService {

    @Value("${asr.local.endpoint:http://localhost:8201}")
    private String endpoint;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String recognize(String audioBase64, String audioFormat, String property) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("audio_base64", audioBase64);
            body.put("audio_format", audioFormat);
            if (property != null) {
                body.put("property", property);
            }

            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + "/recognize"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("本地ASR服务返回 HTTP " + resp.statusCode());
            }

            JsonNode root = mapper.readTree(resp.body());
            String text = root.path("text").asText("");
            log.info("[本地ASR] 识别结果: {}", text);
            return text;
        } catch (Exception e) {
            log.error("[本地ASR] 识别异常: {}", e.getMessage());
            throw new RuntimeException("本地语音识别异常: " + e.getMessage());
        }
    }
}
