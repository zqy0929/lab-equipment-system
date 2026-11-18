package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据可见域（损耗记录 / 信用积分等个人数据）：
 * - 学生、教师：仅本人（课题组只共享【预约信息】，损耗与信用严格隔离）；
 * - 管理员 / 设备保管员 / 安全员：全部可见。
 * 预约数据的课题组共享可见域见 GroupService.visibleReservationUserIds。
 */
@Service
@RequiredArgsConstructor
public class GroupScopeService {

    private final UserMapper userMapper;

    public enum Scope { SELF, ALL }

    /** 当前用户个人数据（损耗 / 信用）可见域 */
    public Scope scopeOf(User user) {
        String role = user == null ? null : user.getRole();
        if ("STUDENT".equals(role) || "USER".equals(role) || "TEACHER".equals(role)) return Scope.SELF;
        return Scope.ALL;
    }

    /** 某角色的全部启用用户（用于站内通知） */
    public List<User> usersByRole(String role) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, role)
                .eq(User::getStatus, 1));
    }
}
