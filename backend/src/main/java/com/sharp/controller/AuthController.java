package com.sharp.controller;

import com.sharp.common.CurrentUser;
import com.sharp.common.Result;
import com.sharp.dto.AuthRequest;
import com.sharp.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** 登录 / 注册。这些接口在 AuthInterceptor 中被放行（无需 token）。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<Map<String, String>> register(@RequestBody AuthRequest req) {
        String token = authService.register(req.getUsername(), req.getPassword(), req.getInviteCode());
        return Result.ok(payload(token, req.getUsername().trim()));
    }

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody AuthRequest req) {
        String token = authService.login(req.getUsername(), req.getPassword());
        return Result.ok(payload(token, req.getUsername().trim()));
    }

    /** 忘记密码：邀请码 + 用户名 + 新密码，重置后直接登录。 */
    @PostMapping("/reset-password")
    public Result<Map<String, String>> resetPassword(@RequestBody AuthRequest req) {
        String token = authService.resetPassword(req.getUsername(), req.getInviteCode(), req.getPassword());
        return Result.ok(payload(token, req.getUsername().trim()));
    }

    /** 当前登录用户（需带 token；此接口不在 auth/** 放行范围，走拦截器）。 */
    @GetMapping("/me")
    public Result<Map<String, String>> me() {
        Map<String, String> m = new HashMap<>();
        m.put("username", CurrentUser.get());
        return Result.ok(m);
    }

    private Map<String, String> payload(String token, String username) {
        Map<String, String> m = new HashMap<>();
        m.put("token", token);
        m.put("username", username);
        return m;
    }
}
