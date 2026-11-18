package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.dto.ViolationCreateDTO;
import com.lab.equipment.entity.Message;
import com.lab.equipment.entity.User;
import com.lab.equipment.entity.ViolationRecord;
import com.lab.equipment.mapper.UserMapper;
import com.lab.equipment.mapper.ViolationRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 信用积分与违约管理 */
@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserMapper userMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final SysConfigService sysConfigService;
    private final MessageService messageService;

    /** 预约前信用校验：低于阈值禁止预约 */
    public void ensureCanReserve(User user) {
        int score = user.getCreditScore() == null ? 100 : user.getCreditScore();
        int threshold = sysConfigService.getInt(SysConfigService.KEY_CREDIT_THRESHOLD);
        if (score < threshold) {
            throw new BusinessException(
                    "信用积分不足（当前 " + score + " 分，低于阈值 " + threshold + " 分），暂时无法预约，请联系管理员");
        }
    }

    /** 管理员登记违约：写记录 + 扣分 + 站内消息通知本人 */
    @Transactional
    public void registerViolation(ViolationCreateDTO dto, User operator) {
        if (!ViolationRecord.NO_SHOW.equals(dto.getType())
                && !ViolationRecord.OVERDUE.equals(dto.getType())) {
            throw new BusinessException("违约类型不合法");
        }
        User user = userMapper.selectById(dto.getUserId());
        if (user == null) throw new BusinessException("用户不存在");
        applyViolation(user, dto.getReservationId(), dto.getType(), dto.getDeduction(),
                dto.getRemark(), operator == null ? null : operator.getId());
    }

    /**
     * 系统自动登记违约（无人工操作人，如预约自动过期）：
     * 写违约记录 + 扣分 + 消息通知；同一预约同一类型只登记一次，避免重复扣分。
     */
    @Transactional
    public void autoRegisterViolation(Long userId, Long reservationId, String type,
                                      int deduction, String remark) {
        if (reservationId != null) {
            Long exists = violationRecordMapper.selectCount(new QueryWrapper<ViolationRecord>()
                    .eq("user_id", userId).eq("reservation_id", reservationId).eq("type", type));
            if (exists != null && exists > 0) return;
        }
        User user = userMapper.selectById(userId);
        if (user == null) return;
        applyViolation(user, reservationId, type, deduction, remark, null);
    }

    /** 违约落地：插记录、扣分（不低于 0）、推送消息 */
    private void applyViolation(User user, Long reservationId, String type, int deduction,
                                String remark, Long operatorId) {
        ViolationRecord v = new ViolationRecord();
        v.setUserId(user.getId());
        v.setReservationId(reservationId);
        v.setType(type);
        v.setDeduction(deduction);
        v.setRemark(remark == null ? "" : remark);
        v.setOperatorId(operatorId);
        violationRecordMapper.insert(v);

        int old = user.getCreditScore() == null ? 100 : user.getCreditScore();
        int now = Math.max(0, old - deduction);
        user.setCreditScore(now);
        userMapper.updateById(user);

        String typeLabel = ViolationRecord.NO_SHOW.equals(type) ? "预约爽约" : "超时归还设备";
        messageService.push(user.getId(), "信用分扣减提醒",
                "您因「" + typeLabel + "」被登记违约，扣除信用分 " + deduction
                        + " 分（" + old + " → " + now + "）。" + v.getRemark(),
                Message.TYPE_SYSTEM);
    }

    /** 用户信用列表（分页，支持关键字搜索） */
    public Page<User> creditPage(String keyword, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword));
        }
        wrapper.orderByAsc(User::getCreditScore);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }

    /** 违约记录（分页，可按用户/类型过滤） */
    public Page<ViolationRecord> violationPage(Long userId, String type, long page, long size) {
        QueryWrapper<ViolationRecord> wrapper = new QueryWrapper<>();
        if (userId != null) wrapper.eq("v.user_id", userId);
        if (StringUtils.hasText(type)) wrapper.eq("v.type", type);
        wrapper.orderByDesc("v.create_time");
        return violationRecordMapper.selectWithUserPage(new Page<>(page, size), wrapper);
    }

    /** 我的违约记录（不分页） */
    public Object myViolations(Long userId) {
        QueryWrapper<ViolationRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("v.user_id", userId).orderByDesc("v.create_time");
        return violationRecordMapper.selectWithUserList(wrapper);
    }
}
