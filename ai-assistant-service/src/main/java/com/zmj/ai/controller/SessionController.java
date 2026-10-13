package com.zmj.ai.controller;

import com.zmj.ai.service.SessionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 查询用户的历史会话列表 */
    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam Long userId) {
        return Map.of("code", 200, "data", sessionService.listByUser(userId));
    }

    /** 重命名会话 */
    @PostMapping("/{sessionId}/rename")
    public Map<String, Object> rename(@PathVariable String sessionId, @RequestParam String title) {
        sessionService.rename(sessionId, title);
        return Map.of("code", 200, "msg", "已重命名");
    }

    /** 删除会话（历史对话删除） */
    @DeleteMapping("/{sessionId}")
    public Map<String, Object> delete(@PathVariable String sessionId) {
        sessionService.delete(sessionId);
        return Map.of("code", 200, "msg", "已删除");
    }
}
