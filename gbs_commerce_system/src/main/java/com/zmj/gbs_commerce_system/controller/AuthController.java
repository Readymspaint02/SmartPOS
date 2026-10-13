package com.zmj.gbs_commerce_system.controller;

import com.zmj.gbs_commerce_system.annotation.RateLimit;
import com.zmj.gbs_commerce_system.entity.Role;
import com.zmj.gbs_commerce_system.entity.User;
import com.zmj.gbs_commerce_system.metrics.BusinessMetrics;
import com.zmj.gbs_commerce_system.service.FaceAuthService;
import com.zmj.gbs_commerce_system.service.RoleService;
import com.zmj.gbs_commerce_system.service.TokenBlacklistService;
import com.zmj.gbs_commerce_system.service.UserService;
import com.zmj.gbs_commerce_system.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@Validated
@Tag(name = "登录注册接口")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private FaceAuthService faceAuthService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private BusinessMetrics businessMetrics;

    @GetMapping("/roles")
    @Operation(summary = "获取可登录角色列表（登录页下拉框用）")
    public Map<String, Object> listRoles() {
        List<Role> roles = roleService.findAllRoles();
        if (roles != null) {
            roles.removeIf(r -> r.getStatus() != null && r.getStatus() == 1);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", roles);
        return result;
    }

    /**
     * 混合登录方案：输入账号后自动带出该账号拥有的可用角色。
     * 仅返回角色信息（roleKey/roleName），不泄露密码等敏感字段。
     * 账号不存在或无角色时返回空列表，下拉框显示提示。
     * 注意：该接口按用户名查询角色，存在轻微用户名存在性泄露，仅适用于内网收银系统。
     */
    @GetMapping("/userRoles")
    @Operation(summary = "根据用户名带出该账号拥有的可用角色（登录页混合方案）")
    public Map<String, Object> listRolesByUsername(@RequestParam String username) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        if (username == null || username.trim().isEmpty()) {
            result.put("data", List.of());
            return result;
        }
        User user = userService.findByUsername(username.trim());
        List<Role> roles = new java.util.ArrayList<>(
                (user != null && user.getRoles() != null) ? user.getRoles() : List.of());
        // 过滤掉已禁用的角色
        roles.removeIf(r -> r.getStatus() != null && r.getStatus() == 1);
        result.put("data", roles);
        return result;
    }

    @PostMapping("/login")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功"),
            @ApiResponse(responseCode = "500", description = "用户名或密码错误")
    })
    @Operation(summary = "用户登录")
    @RateLimit(value = 5, windowSeconds = 60, limitType = RateLimit.LimitType.IP, message = "登录请求过于频繁，请稍后再试")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        UsernamePasswordToken token = new UsernamePasswordToken(
                request.getUsername(),
                request.getPassword()
        );

        Subject subject = SecurityUtils.getSubject();
        try {
            subject.login(token);
            User user = (User) subject.getPrincipal();

            // 选择指定角色登录时，校验账号是否拥有该角色
            if (request.getRoleKey() != null && !request.getRoleKey().isEmpty()) {
                boolean hasRole = user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(r -> request.getRoleKey().equals(r.getRoleKey()));
                if (!hasRole) {
                    subject.logout();
                    businessMetrics.incrementLoginFail();
                    Map<String, Object> denied = new HashMap<>();
                    denied.put("code", 500);
                    denied.put("msg", "该账号不属于所选角色，请确认后重试");
                    return denied;
                }
            }

            String jwtToken = JwtUtils.generateToken(user.getId(), user.getUsername());

            businessMetrics.incrementLoginSuccess();

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("msg", "登录成功");
            result.put("data", new AuthResponse(jwtToken, user));
            return result;
        } catch (AuthenticationException e) {
            businessMetrics.incrementLoginFail();
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("msg", "用户名或密码错误");
            return result;
        }
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.findByUsername(request.getUsername()) != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("msg", "用户名已存在");
            return result;
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname());

        if (userService.registerUser(user)) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("msg", "注册成功");
            return result;
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("msg", "注册失败");
            return result;
        }
    }

    @PostMapping("/faceLogin")
    @Operation(summary = "人脸识别登录")
    @RateLimit(value = 10, windowSeconds = 60, limitType = RateLimit.LimitType.IP, message = "人脸登录请求过于频繁，请稍后再试")
    public Map<String, Object> faceLogin(@Valid @RequestBody FaceLoginRequest request) {
        Map<String, Object> result = new HashMap<>();

        Optional<String> matchedUserId = faceAuthService.matchAndGetUsername(request.getImage());
        if (matchedUserId.isEmpty()) {
            businessMetrics.incrementFaceLoginFail();
            result.put("code", 500);
            result.put("msg", "未匹配到注册用户或识别失败");
            return result;
        }

        User user = null;
        try {
            user = userService.findUserById(Long.valueOf(matchedUserId.get()));
        } catch (NumberFormatException e) {
        }
        if (user == null) {
            user = userService.findByUsername(matchedUserId.get());
        }
        
        if (user == null || (user.getStatus() != null && user.getStatus() == 1)) {
            businessMetrics.incrementFaceLoginFail();
            result.put("code", 500);
            result.put("msg", "用户不存在或已被禁用");
            return result;
        }

        businessMetrics.incrementFaceLoginSuccess();
        String jwtToken = JwtUtils.generateToken(user.getId(), user.getUsername());
        result.put("code", 200);
        result.put("msg", "人脸登录成功");
        result.put("data", new AuthResponse(jwtToken, user));
        return result;
    }

    @GetMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = JwtUtils.parseToken(token);
                long exp = claims.getExpiration().getTime();
                long ttl = exp - System.currentTimeMillis();
                if (ttl > 0) {
                    tokenBlacklistService.addToBlacklist(token, ttl);
                }
            } catch (Exception ignored) {
            }
        }
        SecurityUtils.getSubject().logout();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "退出成功");
        return result;
    }

    public static class LoginRequest {
        @Schema(description = "用户名", example = "admin")
        private String username;

        @Schema(description = "密码", example = "admin123")
        private String password;

        @Schema(description = "登录角色roleKey（可选，指定后校验账号是否拥有该角色）")
        private String roleKey;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRoleKey() {
            return roleKey;
        }

        public void setRoleKey(String roleKey) {
            this.roleKey = roleKey;
        }
    }

    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;
        private String nickname;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
    }

    public static class FaceLoginRequest {
        @NotBlank(message = "人脸图像不能为空")
        private String image;

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }
    }

    public static class AuthResponse {
        private final String token;
        private final User user;

        public AuthResponse(String token, User user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() {
            return token;
        }

        public User getUser() {
            return user;
        }
    }
}
