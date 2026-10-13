package com.zmj.ai.service;

import com.zmj.ai.config.AiProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话服务：流式输出（SSE）+ 打断停止 + 会话标题总结。
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final SessionService sessionService;
    private final AiProperties props;

    /** sessionId -> 进行中的流订阅，用于打断 */
    private final Map<String, Disposable> streams = new ConcurrentHashMap<>();
    private final ExecutorService titleExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "title-generator");
        t.setDaemon(true);
        return t;
    });

    public ChatService(ChatClient chatClient, SessionService sessionService, AiProperties props) {
        this.chatClient = chatClient;
        this.sessionService = sessionService;
        this.props = props;
    }

    /** 流式对话：SSE 逐字推送，前端可随时调用 stop 打断 */
    public SseEmitter chatStream(String sessionId, Long userId, String message) {
        SseEmitter emitter = new SseEmitter(0L);
        boolean isNew = sessionService.ensureSession(sessionId, userId, message);
        sessionService.incrementCount(sessionId);

        StringBuilder answer = new StringBuilder();
        Disposable disposable = chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .subscribe(
                        chunk -> {
                            answer.append(chunk);
                            try {
                                emitter.send(chunk);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        err -> {
                            streams.remove(sessionId);
                            emitter.completeWithError(err);
                        },
                        () -> {
                            streams.remove(sessionId);
                            emitter.complete();
                            if (isNew) {
                                generateTitleAsync(sessionId, message, answer.toString());
                            }
                        });
        streams.put(sessionId, disposable);
        return emitter;
    }

    /** 非流式对话（测试/降级用） */
    public String chat(String sessionId, Long userId, String message) {
        boolean isNew = sessionService.ensureSession(sessionId, userId, message);
        sessionService.incrementCount(sessionId);
        String answer = chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        if (isNew) {
            generateTitleAsync(sessionId, message, answer);
        }
        return answer;
    }

    /** 打断流式输出：释放订阅，立即停止生成 */
    public void stop(String sessionId) {
        Disposable disposable = streams.remove(sessionId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    /** 标题总结：首轮对话后异步生成简短标题，写入会话表 */
    private void generateTitleAsync(String sessionId, String question, String answer) {
        titleExecutor.execute(() -> {
            try {
                String title = chatClient.prompt()
                        .user(props.getTitlePrompt() + "\n用户：" + question + "\n助手：" + truncate(answer, 200))
                        .call()
                        .content();
                if (title != null && !title.isBlank()) {
                    sessionService.rename(sessionId, title.replaceAll("[\"'\\n]", "").trim());
                }
            } catch (Exception ignored) {
                // 标题生成失败不影响主流程，保留首串截断标题
            }
        });
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
