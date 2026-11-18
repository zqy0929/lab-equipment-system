package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.JwtUtil;
import com.lab.equipment.dto.LoginDTO;
import com.lab.equipment.dto.RegisterDTO;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public Map<String, Object> login(LoginDTO dto) {
        String input = dto.getUsername() == null ? "" : dto.getUsername().trim();
        // 支持两种登录方式：用户名 + 密码，或 学号/工号 + 密码
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .and(w -> w.eq(User::getUsername, input).or().eq(User::getEmployeeNo, input)));
        if (user == null || !encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名 / 学号或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        // 待审核 / 已驳回账号：暂不具备所申请角色权限，按学生基础权限使用
        if (!"APPROVED".equals(user.getAuditStatus())) {
            user.setRole("STUDENT");
        }
        String token = jwtUtil.generate(user.getId());
        user.setPassword(null);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }

    public void register(RegisterDTO dto) {
        String employeeNo = dto.getEmployeeNo() == null ? "" : dto.getEmployeeNo().trim();
        // 用户名唯一
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已被注册");
        }
        // 学号/工号全局唯一：同一身份只能注册 1 个账号，不能换角色重复注册
        Long empCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmployeeNo, employeeNo));
        if (empCount > 0) {
            throw new BusinessException("该学号 / 工号已注册账号，不可重复注册");
        }
        String role = normalizeRole(dto.getRole());
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(encoder.encode(dto.getPassword()));
        user.setEmployeeNo(employeeNo);
        user.setRealName(dto.getRealName());
        user.setGender(dto.getGender());
        user.setDepartment(dto.getDepartment());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(role);
        // 学生：直接激活生效；其余角色：待审核，审核通过前仅学生基础权限
        if ("STUDENT".equals(role)) {
            user.setAuditStatus("APPROVED");
        } else {
            user.setAuditStatus("PENDING");
        }
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    /** 注册角色白名单：五类角色均可申请；非学生角色需管理员审核后生效 */
    private String normalizeRole(String role) {
        String value = role == null ? "" : role.trim().toUpperCase();
        return switch (value) {
            case "STUDENT", "学生" -> "STUDENT";
            case "TEACHER", "教师", "老师" -> "TEACHER";
            case "SAFETY", "安全员" -> "SAFETY";
            case "KEEPER", "设备保管员", "保管员" -> "KEEPER";
            case "ADMIN", "管理员" -> "ADMIN";
            default -> throw new BusinessException("请选择有效的注册角色");
        };
    }
}
