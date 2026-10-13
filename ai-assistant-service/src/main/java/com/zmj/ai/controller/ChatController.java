package com.zmj.ai.controller;

import com.zmj.ai.dto.ChatRequest;
import com.zmj.ai.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/ai/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 流式对话：SSE 逐字输出 */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.chatStream(request.getSessionId(), request.getUserId(), request.getMessage());
    }

    /** 非流式对话 */
    @PostMapping
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest request) {
        String answer = chatService.chat(request.getSessionId(), request.getUserId(), request.getMessage());
        return Map.of("code", 200, "data", answer);
    }

    /** 打断/停止生成 */
    @PostMapping("/stop/{sessionId}")
    public Map<String, Object> stop(@PathVariable String sessionId) {
        chatService.stop(sessionId);
        return Map.of("code", 200, "msg", "已停止生成");
    }
}
