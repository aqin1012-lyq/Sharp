package com.sharp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharp.common.CurrentUser;
import com.sharp.common.Result;
import com.sharp.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 校验 Authorization: Bearer <jwt>，通过则把用户名放入 CurrentUser。 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        String username = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            username = jwtService.parseUsername(auth.substring(7).trim());
        }
        if (username == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    new Result<>(401, "未登录或登录已过期", null)));
            return false;
        }
        CurrentUser.set(username);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUser.clear();
    }
}
