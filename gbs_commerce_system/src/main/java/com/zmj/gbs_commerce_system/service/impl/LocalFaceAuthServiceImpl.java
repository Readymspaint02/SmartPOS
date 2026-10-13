package com.zmj.gbs_commerce_system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmj.gbs_commerce_system.service.FaceAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Optional;

/**
 * 本地人脸识别实现（策略模式 local 分支）。
 * 依赖本地部署的人脸推理服务（如 InsightFace / SeetaFace 封装的 REST 服务），
 * 约定协议：
 *   POST {endpoint}/register {"user_id","image"} -> {"success":bool,"message":str}
 *   POST {endpoint}/search   {"image"}           -> {"user_id":str|null,"score":float}
 *   POST {endpoint}/status   {"user_id"}         -> {"registered":bool}
 */
@Service
@ConditionalOnProperty(name = "face.provider", havingValue = "local")
public class LocalFaceAuthServiceImpl implements FaceAuthService {

    private static final Logger log = LoggerFactory.getLogger(LocalFaceAuthServiceImpl.class);
    private static final double MATCH_THRESHOLD = 80.0;

    @Value("${face.local.endpoint:http://localhost:8200}")
    private String endpoint;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public Optional<String> matchAndGetUsername(String base64Image) {
        try {
            JsonNode root = postJson("/search", Map.of("image", base64Image));
            String userId = root.path("user_id").asText(null);
            double score = root.path("score").asDouble(0);
            if (userId != null && !userId.isEmpty() && score >= MATCH_THRESHOLD) {
                log.info("[本地人脸] 匹配成功: userId={}, score={}", userId, score);
                return Optional.of(userId);
            }
            log.info("[本地人脸] 未匹配: userId={}, score={}", userId, score);
            return Optional.empty();
        } catch (Exception e) {
            log.error("[本地人脸] 搜索异常", e);
            return Optional.empty();
        }
    }

    @Override
    public RegisterResult registerFace(String userId, String base64Image, String userInfo) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", userId);
            body.put("image", base64Image);
            if (userInfo != null && !userInfo.isEmpty()) {
                body.put("user_info", userInfo);
            }
            JsonNode root = postJson("/register", body);
            boolean success = root.path("success").asBoolean(false);
            String message = root.path("message").asText(success ? "注册成功" : "注册失败");
            log.info("[本地人脸] 注册 userId={}: {}", userId, message);
            return success ? RegisterResult.success() : RegisterResult.fail(message);
        } catch (Exception e) {
            log.error("[本地人脸] 注册异常", e);
            return RegisterResult.fail("本地人脸服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyFaceForMember(String base64Image, String expectedUserId) {
        Optional<String> matched = matchAndGetUsername(base64Image);
        boolean ok = matched.isPresent() && matched.get().equals(expectedUserId);
        log.info("[本地人脸] 验证 expectedUserId={}, matched={}, result={}",
                expectedUserId, matched.orElse("none"), ok ? "成功" : "失败");
        return ok;
    }

    @Override
    public boolean checkFaceRegistered(String userId) {
        try {
            JsonNode root = postJson("/status", Map.of("user_id", userId));
            boolean registered = root.path("registered").asBoolean(false);
            log.info("[本地人脸] 用户{}注册状态: {}", userId, registered ? "已注册" : "未注册");
            return registered;
        } catch (Exception e) {
            log.error("[本地人脸] 查询注册状态异常", e);
            return false;
        }
    }

    private JsonNode postJson(String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("本地人脸服务返回 HTTP " + resp.statusCode());
        }
        return mapper.readTree(resp.body());
    }
}
