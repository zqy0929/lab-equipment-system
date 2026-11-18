package com.lab.equipment.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.UserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 登录拦截器：校验 JWT，写入 UserContext；按路径做角色级鉴权（ADMIN/TEACHER/KEEPER/SAFETY） */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long userId = null;
        if (token != null && !token.isBlank()) {
            try {
                userId = jwtUtil.parse(token);
            } catch (JwtException | IllegalArgumentException ignored) { }
        }
        if (userId == null) {
            writeJson(response, 401, "未登录或登录已过期");
            return false;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == 0) {
            writeJson(response, 401, "账号不存在或已被禁用");
            return false;
        }
        // 审核兜底：待审核 / 已驳回账号不具备所申请角色权限，一律按学生基础权限鉴权
        if (!"APPROVED".equals(user.getAuditStatus())) {
            user.setRole("STUDENT");
        }
        String uri = request.getRequestURI();
        String role = user.getRole();
        if (uri.startsWith("/api/admin")) {
            // 借用审批：仅管理员与设备保管员（教师无审批权限）；设备/损耗管理：管理员与设备保管员；其余后台接口仅管理员
            boolean approverAllowed = uri.startsWith("/api/admin/reservations")
                    && ("ADMIN".equals(role) || "KEEPER".equals(role));
            boolean keeperAllowed = (uri.startsWith("/api/admin/equipment")
                    || uri.startsWith("/api/admin/damages"))
                    && ("ADMIN".equals(role) || "KEEPER".equals(role));
            if (!"ADMIN".equals(role) && !approverAllowed && !keeperAllowed) {
                writeJson(response, 403, "无权限：该操作仅管理员可用");
                return false;
            }
        }
        // 安全中心（高危使用记录 / 隐患处理）：安全员与管理员
        if (uri.startsWith("/api/safety") && !"ADMIN".equals(role) && !"SAFETY".equals(role)) {
            writeJson(response, 403, "无权限：该页面仅安全员与管理员可访问");
            return false;
        }
        user.setPassword(null);
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeJson(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
    }
}
