package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.BanLift;
import com.lab.equipment.entity.CancelRecord;
import com.lab.equipment.entity.Message;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.BanLiftMapper;
import com.lab.equipment.mapper.CancelRecordMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预约取消限制：30 天内主动取消计数 + 超限封禁 + 管理员解禁。
 *
 * 计次规则（按取消时距预约开始的时长）：
 * - 开始前 24 小时以上取消：不计入（不写记录）
 * - 开始前 24 小时 ~ 3 小时取消：计 1 次
 * - 开始前 3 小时内取消：计 2 次（防止临近时间恶意占机后取消）
 * 管理员审批驳回、系统自动过期不属于用户主动取消，不计入。
 */
@Service
@RequiredArgsConstructor
public class CancelBanService {

    /** 封禁校验失败统一提示（XX 为剩余解禁天数） */
    public static String banMessage(int remainingDays) {
        return "您近 30 天取消预约次数超限，暂时无法预约设备，剩余解禁 " + remainingDays + " 天";
    }

    private final CancelRecordMapper cancelRecordMapper;
    private final BanLiftMapper banLiftMapper;
    private final com.lab.equipment.mapper.UserMapper userMapper;
    private final SysConfigService sysConfigService;
    private final OpLogService opLogService;
    private final MessageService messageService;

    /* ---------------- 取消记录 ---------------- */

    /**
     * 用户主动取消预约时调用：按距开始时长写入计次记录。
     * 返回本次计入的次数（0 表示不计入）。
     */
    @Transactional
    public int recordCancel(Long userId, Long reservationId, LocalDateTime startTime) {
        long hoursToStart = Duration.between(LocalDateTime.now(), startTime).toHours();
        int weight;
        String detail;
        if (hoursToStart >= 24) {
            return 0; // 开始前 24 小时以上取消不计入
        } else if (hoursToStart >= 3) {
            weight = 1;
            detail = "开始前 " + hoursToStart + " 小时取消，计 1 次";
        } else {
            weight = 2;
            detail = "开始前 " + hoursToStart + " 小时取消，计 2 次";
        }
        CancelRecord record = new CancelRecord();
        record.setUserId(userId);
        record.setReservationId(reservationId);
        record.setCancelTime(LocalDateTime.now());
        record.setWeight(weight);
        record.setDetail(detail);
        cancelRecordMapper.insert(record);
        return weight;
    }

    /* ---------------- 统计与封禁判定 ---------------- */

    /** 取消统计结果（本人看板 / 个人中心 / 预约页共用） */
    @Data
    public static class Stats {
        private int count30d;          // 近 30 天取消计次（加权）
        private int limit;             // 系统配置阈值
        private boolean banned;        // 是否处于封禁中
        private String banUntil;       // 封禁截止时间（ISO，未封禁为 null）
        private int remainingDays;     // 剩余解禁天数（封禁中 >= 1，未封禁 0）
        private String lastCancelTime; // 最近一次计入取消的时间
    }

    /** 查询用户取消统计与封禁状态 */
    public Stats stats(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(30);
        List<CancelRecord> records = cancelRecordMapper.selectList(new LambdaQueryWrapper<CancelRecord>()
                .eq(CancelRecord::getUserId, userId)
                .ge(CancelRecord::getCancelTime, windowStart)
                .orderByDesc(CancelRecord::getCancelTime));
        int count = records.stream().mapToInt(r -> r.getWeight() == null ? 1 : r.getWeight()).sum();
        int limit = sysConfigService.getInt(SysConfigService.KEY_CANCEL_LIMIT_COUNT);
        int banDays = sysConfigService.getInt(SysConfigService.KEY_CANCEL_BAN_DAYS);

        Stats s = new Stats();
        s.setCount30d(count);
        s.setLimit(limit);
        if (!records.isEmpty()) {
            s.setLastCancelTime(records.get(0).getCancelTime().toString());
        }
        // 超限判定：30 天加权计次 > 阈值 → 自最近一次计入取消时间起封禁 banDays 天；
        // 若管理员在「触发封禁的那次取消」之后手动解除过，则豁免（后续再取消会重新判定）
        if (count > limit && !records.isEmpty()) {
            LocalDateTime latest = records.get(0).getCancelTime();
            LocalDateTime banUntil = latest.plusDays(banDays);
            boolean lifted = banLiftMapper.selectCount(new LambdaQueryWrapper<BanLift>()
                    .eq(BanLift::getUserId, userId)
                    .ge(BanLift::getLiftTime, latest)) > 0;
            if (banUntil.isAfter(now) && !lifted) {
                s.setBanned(true);
                s.setBanUntil(banUntil.toString());
                long hours = Duration.between(now, banUntil).toHours();
                s.setRemainingDays((int) Math.max(1, (hours + 23) / 24));
            }
        }
        return s;
    }

    /** 提交新预约前的封禁校验（超限抛业务异常，前端展示剩余解禁天数） */
    public void ensureCanCreate(User user) {
        Stats s = stats(user.getId());
        if (s.isBanned()) {
            throw new BusinessException(banMessage(s.getRemainingDays()));
        }
    }

    /* ---------------- 管理员：取消记录 / 封禁名单 / 解禁 ---------------- */

    /** 全体用户取消记录分页（可按用户名/姓名搜索） */
    public Page<CancelRecord> recordPage(String keyword, long page, long size) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CancelRecord> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("u.username", keyword.trim()).or().like("u.real_name", keyword.trim()));
        }
        wrapper.orderByDesc("c.cancel_time");
        return cancelRecordMapper.selectWithUserPage(new Page<>(page, size), wrapper);
    }

    /** 当前处于封禁中的用户名单 */
    public List<Map<String, Object>> bannedUsers() {
        // 全体有取消记录的用户逐个判定（数据量小，直接遍历去重用户）
        List<CancelRecord> all = cancelRecordMapper.selectList(new LambdaQueryWrapper<CancelRecord>()
                .ge(CancelRecord::getCancelTime, LocalDateTime.now().minusDays(30)));
        return all.stream().map(CancelRecord::getUserId).distinct()
                .map(uid -> {
                    Stats s = stats(uid);
                    if (!s.isBanned()) return null;
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("userId", uid);
                    User u = userMapper.selectById(uid);
                    m.put("username", u != null ? u.getUsername() : null);
                    m.put("realName", u != null ? u.getRealName() : null);
                    m.put("role", u != null ? u.getRole() : null);
                    m.put("count30d", s.getCount30d());
                    m.put("banUntil", s.getBanUntil());
                    m.put("remainingDays", s.getRemainingDays());
                    return m;
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    /** 管理员/保管员手动解除封禁（写记录 + 通知本人 + 审计日志） */
    @Transactional
    public void liftBan(Long userId, User operator) {
        Stats s = stats(userId);
        if (!s.isBanned()) {
            throw new BusinessException("该用户当前不在预约封禁中，无需解除");
        }
        BanLift lift = new BanLift();
        lift.setUserId(userId);
        lift.setLiftTime(LocalDateTime.now());
        lift.setOperatorId(operator.getId());
        lift.setOperatorName(operator.getRealName() != null ? operator.getRealName() : operator.getUsername());
        banLiftMapper.insert(lift);
        opLogService.log("LIFT_CANCEL_BAN", "解除用户预约封禁：用户 #" + userId
                + "（近 30 天取消 " + s.getCount30d() + " 次，原解禁剩余 " + s.getRemainingDays() + " 天）");
        messageService.push(userId, "预约封禁已解除",
                "管理员已手动解除您的预约封禁限制，现在可以正常提交设备预约。请合理规划预约时间，避免临近开始时取消。",
                Message.TYPE_SYSTEM);
    }
}
