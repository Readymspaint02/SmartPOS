package com.zmj.gbs_commerce_system.handler;

import com.huawei.sis.bean.AuthInfo;
import com.huawei.sis.bean.RasrListener;
import com.huawei.sis.bean.SisConfig;
import com.huawei.sis.bean.request.SasrWebsocketRequest;
import com.huawei.sis.bean.response.RasrResponse;
import com.huawei.sis.bean.response.StateResponse;
import com.huawei.sis.bean.base.RasrSentence;
import com.huawei.sis.client.SasrWebsocketClient;
import com.zmj.gbs_commerce_system.config.HuaweiAsrProperties;
import com.zmj.gbs_commerce_system.metrics.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 语音识别 WebSocket 代理
 *
 * 解决浏览器 WebSocket API 不支持自定义请求头的限制：
 *   浏览器 --(ws)--> 本服务 /asr/stream --(wss,带签名)--> 华为云ASR
 *
 * 协议约定（与前端 SmartAgentWidget.vue 配合）：
 *   - 客户端→服务端：
 *       TextMessage  JSON: {"command":"START","config":{...}}   开启会话
 *       BinaryMessage               PCM 16k 16bit 音频帧
 *       TextMessage  JSON: {"command":"END","cancel":false}   结束
 *   - 服务端→客户端：
 *       TextMessage  JSON: {"resp_type":"RESULT","segments":[...]}
 *       TextMessage  JSON: {"resp_type":"END","text":"..."}
 *       TextMessage  JSON: {"resp_type":"ERROR","error_msg":"..."}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrWebSocketHandler extends TextWebSocketHandler {

    private static final int BEGIN_TIMEOUT_SECONDS = 5;
    private static final int END_TIMEOUT_SECONDS = 15;

    private final HuaweiAsrProperties asrProperties;
    private final BusinessMetrics businessMetrics;

    private final Map<String, RasrBridge> bridges = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        AuthInfo authInfo = new AuthInfo(
                asrProperties.getAk(),
                asrProperties.getSk(),
                asrProperties.getRegion(),
                asrProperties.getProjectId()
        );
        SisConfig config = new SisConfig();
        config.setConnectionTimeout(5000);
        config.setReadTimeout(15000);

        CountDownLatch beginLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        AtomicReference<String> finalText = new AtomicReference<>("");

        WebSocketSession browserSession = session;
        SasrWebsocketClient huaweiClient = new SasrWebsocketClient(authInfo, new RasrListener() {
            @Override
            public void onTranscriptionConnect() {}

            @Override
            public void onTranscriptionClose() {}

            @Override
            public void onTranscriptionResponse(RasrResponse response) {
                try {
                    if (response.getSentenceList() == null || response.getSentenceList().isEmpty()) {
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (RasrSentence sentence : response.getSentenceList()) {
                        String t = sentence.getResult() != null ? sentence.getResult().getText() : null;
                        if (t != null) {
                            sb.append(t);
                        }
                    }
                    String text = sb.toString();
                    String json = "{\"resp_type\":\"RESULT\",\"text\":\"" + escape(text) + "\"}";
                    if (browserSession.isOpen()) {
                        synchronized (browserSession) {
                            browserSession.sendMessage(new TextMessage(json));
                        }
                    }
                } catch (Exception e) {
                    log.warn("转发识别结果失败", e);
                }
            }

            @Override
            public void onTranscriptionBegin(StateResponse response) {
                beginLatch.countDown();
            }

            @Override
            public void onSTranscriptionEnd(StateResponse response) {
                try {
                    if (browserSession.isOpen()) {
                        synchronized (browserSession) {
                            String text = finalText.get();
                            browserSession.sendMessage(new TextMessage(
                                    "{\"resp_type\":\"END\",\"text\":\"" + escape(text) + "\"}"));
                        }
                    }
                } catch (Exception ignored) {}
                endLatch.countDown();
            }

            @Override
            public void onTranscriptionFail(StateResponse response) {
                try {
                    if (browserSession.isOpen()) {
                        synchronized (browserSession) {
                            browserSession.sendMessage(new TextMessage(
                                    "{\"resp_type\":\"ERROR\",\"error_msg\":\"" + escape(String.valueOf(response)) + "\"}"));
                        }
                    }
                } catch (Exception ignored) {}
                beginLatch.countDown();
                endLatch.countDown();
            }

            @Override
            public void onEvent(String event) {}
        }, config);

        RasrBridge bridge = new RasrBridge(huaweiClient, beginLatch, endLatch, finalText);
        bridges.put(session.getId(), bridge);
        log.info("ASR 代理连接建立 sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        RasrBridge bridge = bridges.get(session.getId());
        if (bridge == null) return;
        String payload = message.getPayload();

        if (payload.contains("\"command\":\"START\"")) {
            try {
                bridge.client.sasrConnect(new SasrWebsocketRequest("pcm16k16bit", "chinese_16k_general"));
                bridge.client.sendStart();
                if (!bridge.beginLatch.await(BEGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    sendError(session, "华为云ASR建立连接超时");
                }
            } catch (Exception e) {
                log.error("建立华为ASR连接失败", e);
                sendError(session, "建立ASR连接失败: " + e.getMessage());
            }
            return;
        }

        if (payload.contains("\"command\":\"END\"")) {
            try {
                bridge.client.sendEnd();
                bridge.endLatch.await(END_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                businessMetrics.incrementAsrSuccess();
            } catch (Exception e) {
                log.warn("结束ASR异常", e);
                businessMetrics.incrementAsrFail();
            }
            return;
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        RasrBridge bridge = bridges.get(session.getId());
        if (bridge == null) return;
        ByteBuffer buf = message.getPayload();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        try {
            bridge.client.sendByte(data);
            businessMetrics.incrementAsrCall();
        } catch (Exception e) {
            log.warn("转发音频帧失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        RasrBridge bridge = bridges.remove(session.getId());
        if (bridge != null && bridge.client != null) {
            try { bridge.client.close(); } catch (Exception ignored) {}
        }
        log.info("ASR 代理连接关闭 sessionId={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("ASR 代理传输错误 sessionId={}: {}", session.getId(), exception.getMessage());
        sendError(session, "传输错误: " + exception.getMessage());
    }

    private void sendError(WebSocketSession session, String msg) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(
                            "{\"resp_type\":\"ERROR\",\"error_msg\":\"" + escape(msg) + "\"}"));
                }
            }
        } catch (Exception ignored) {}
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class RasrBridge {
        final SasrWebsocketClient client;
        final CountDownLatch beginLatch;
        final CountDownLatch endLatch;
        final AtomicReference<String> finalText;

        RasrBridge(SasrWebsocketClient client, CountDownLatch beginLatch, CountDownLatch endLatch, AtomicReference<String> finalText) {
            this.client = client;
            this.beginLatch = beginLatch;
            this.endLatch = endLatch;
            this.finalText = finalText;
        }
    }
}
