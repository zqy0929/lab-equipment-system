package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder;
    private final OpLogService opLogService;

    public Page<User> page(String keyword, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getEmployeeNo, keyword)
                    .or().like(User::getDepartment, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectPage(new Page<>(page, size), wrapper);
    }

    private static final java.util.Set<String> ALLOWED_ROLES =
            java.util.Set.of("ADMIN", "TEACHER", "SAFETY", "KEEPER", "STUDENT", "USER");

    /** 系统最高管理员账号：受保护，禁止删除 / 禁用 / 拉黑 / 变更角色 */
    public static final String SUPER_ADMIN_USERNAME = "admin";

    /** 最高管理员保护校验（用户名不区分大小写） */
    private void ensureNotSuperAdmin(User user) {
        if (user.getUsername() != null && SUPER_ADMIN_USERNAME.equalsIgnoreCase(user.getUsername().trim())) {
            throw new BusinessException("该账号为系统最高管理员，禁止执行此操作");
        }
    }

    public void updateRole(Long id, String role) {
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BusinessException("非法角色");
        }
        // 历史角色值归一：USER 与 STUDENT 同为「学生」，落库统一为 STUDENT，保证角色枚举唯一
        if ("USER".equals(role)) role = "STUDENT";
        User user = requireUser(id);
        ensureNotSuperAdmin(user);
        boolean approved = "APPROVED".equals(user.getAuditStatus());
        if (role.equals(user.getRole()) && approved) {
            return; // 角色未变化且已通过审核，避免无意义的审计记录
        }
        String before = user.getRole();
        user.setRole(role);
        // 管理员直接改角色 = 视为审核通过，所分配角色立即生效
        user.setAuditStatus("APPROVED");
        userMapper.updateById(user);
        opLogService.log("CHANGE_ROLE", "账号「" + user.getUsername() + "」角色变更：" + before + " → " + role);
    }

    /** 注册审核：通过 → 所申请角色生效；驳回 → 维持默认学生权限 */
    public void audit(Long id, String action) {
        if (!"APPROVED".equals(action) && !"REJECTED".equals(action)) {
            throw new BusinessException("非法审核操作");
        }
        User user = requireUser(id);
        ensureNotSuperAdmin(user);
        if (!"PENDING".equals(user.getAuditStatus())) {
            throw new BusinessException("该账号不在待审核状态");
        }
        if ("APPROVED".equals(action)) {
            user.setAuditStatus("APPROVED");
            opLogService.log("AUDIT_USER", "账号「" + user.getUsername() + "」角色申请审核通过：" + user.getRole());
        } else {
            user.setAuditStatus("REJECTED");
            user.setRole("STUDENT"); // 驳回：维持默认学生权限
            opLogService.log("AUDIT_USER", "账号「" + user.getUsername() + "」角色申请已驳回，维持学生权限");
        }
        userMapper.updateById(user);
    }

    /** 黑名单：禁止发起预约 */
    public void updateBlacklist(Long id, Integer blacklisted) {
        User user = requireUser(id);
        ensureNotSuperAdmin(user);
        user.setBlacklisted(blacklisted != null && blacklisted == 1 ? 1 : 0);
        userMapper.updateById(user);
        opLogService.log("CHANGE_BLACKLIST", "账号「" + user.getUsername() + "」"
                + (user.getBlacklisted() == 1 ? "加入" : "移出") + "预约黑名单");
    }

    public void updateStatus(Long id, Integer status) {
        User user = requireUser(id);
        ensureNotSuperAdmin(user);
        user.setStatus(status == 0 ? 0 : 1);
        userMapper.updateById(user);
        opLogService.log("CHANGE_STATUS", "账号「" + user.getUsername() + "」"
                + (user.getStatus() == 1 ? "启用" : "禁用"));
    }

    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码长度不能少于 6 位");
        }
        User user = requireUser(id);
        user.setPassword(encoder.encode(newPassword));
        userMapper.updateById(user);
    }

    public void delete(Long id) {
        User user = requireUser(id);
        ensureNotSuperAdmin(user);
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException("不能删除管理员账号");
        }
        userMapper.deleteById(id);
    }

    public void updateProfile(Long id, String realName, String gender, String department, String phone, String email) {
        User user = requireUser(id);
        if (realName != null) user.setRealName(realName);
        if (gender != null) {
            if (!"男".equals(gender) && !"女".equals(gender)) {
                throw new BusinessException("性别取值仅支持男 / 女");
            }
            user.setGender(gender);
        }
        if (department != null) user.setDepartment(department);
        if (phone != null) user.setPhone(phone);
        if (email != null) user.setEmail(email);
        userMapper.updateById(user);
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        return user;
    }
}
