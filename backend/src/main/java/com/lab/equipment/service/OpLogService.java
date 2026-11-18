package com.lab.equipment.service;

import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.OpLog;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.OpLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 操作审计日志：关键操作自动记录（操作人/时间/内容/IP），仅增查，禁止修改删除 */
@Service
@RequiredArgsConstructor
public class OpLogService {

    private final OpLogMapper opLogMapper;

    /** 记录当前登录用户的操作；无登录上下文（定时任务）时记为 system */
    public void log(String action, String detail) {
        try {
            OpLog log = new OpLog();
            User user = UserContext.get();
            if (user != null) {
                log.setUserId(user.getId());
                log.setUsername(user.getUsername());
            } else {
                log.setUsername("system");
            }
            log.setAction(action);
            log.setDetail(detail);
            log.setIp(currentIp());
            opLogMapper.insert(log);
        } catch (Exception ignored) {
            // 审计日志写入失败不影响主业务
        }
    }

    /** 客户端 IP（优先 X-Forwarded-For） */
    public static String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
