package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.entity.Message;
import com.lab.equipment.entity.Reservation;
import com.lab.equipment.entity.User;
import com.lab.equipment.entity.Waitlist;
import com.lab.equipment.mapper.WaitlistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 预约候补队列：约满可排队，空位释放时自动为队首用户转正预约（校验不通过降级为限时资格通知） */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistMapper waitlistMapper;
    private final MessageService messageService;
    private final SysConfigService sysConfigService;
    private final com.lab.equipment.mapper.UserMapper userMapper;
    private final com.lab.equipment.mapper.ReservationMapper reservationMapper;
    private final com.lab.equipment.mapper.EquipmentMapper equipmentMapper;
    private final ManualService manualService;
    private final CancelBanService cancelBanService;
    private final CategoryRuleService categoryRuleService;
    private final OpLogService opLogService;

    /** 加入候补队列（该时段已有占用时允许排队） */
    @Transactional
    public Waitlist join(Long equipmentId, Long userId, LocalDateTime start, LocalDateTime end) {
        // 同一时段重复排队拦截
        Long dup = waitlistMapper.selectCount(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getEquipmentId, equipmentId)
                .eq(Waitlist::getUserId, userId)
                .eq(Waitlist::getStartTime, start)
                .eq(Waitlist::getEndTime, end)
                .in(Waitlist::getStatus, Waitlist.WAITING, Waitlist.NOTIFIED));
        if (dup > 0) {
            throw new BusinessException("您已在该时段的候补队列中，无需重复加入");
        }
        Waitlist w = new Waitlist();
        w.setEquipmentId(equipmentId);
        w.setUserId(userId);
        w.setStartTime(start);
        w.setEndTime(end);
        w.setStatus(Waitlist.WAITING);
        waitlistMapper.insert(w);

        int ahead = countAhead(w);
        messageService.push(userId, "候补排队成功",
                "您已加入该设备时段的候补队列，当前排在第 " + (ahead + 1) + " 位。空位释放后系统将自动为您创建预约申请（待审批），并以站内消息通知您。",
                Message.TYPE_RESERVATION);
        return w;
    }

    /** 当前排队位次（之前还有多少人） */
    public int countAhead(Waitlist w) {
        return Math.toIntExact(waitlistMapper.selectCount(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getEquipmentId, w.getEquipmentId())
                .eq(Waitlist::getStatus, Waitlist.WAITING)
                .lt(Waitlist::getCreateTime, w.getCreateTime())));
    }

    /**
     * 空位释放后处理候补队首用户：优先自动转正（创建待审批预约并站内消息通知），
     * 校验不通过时降级为限时预约资格通知。队首一位获得处理。
     */
    @Transactional
    public void notifyOnSlotFreed(Long equipmentId, LocalDateTime freedStart, LocalDateTime freedEnd) {
        List<Waitlist> waiting = waitlistMapper.selectList(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getEquipmentId, equipmentId)
                .eq(Waitlist::getStatus, Waitlist.WAITING)
                .orderByAsc(Waitlist::getCreateTime));
        for (Waitlist w : waiting) {
            if (w.getStartTime().isBefore(freedEnd) && w.getEndTime().isAfter(freedStart)) {
                if (!tryAutoPromote(w)) {
                    // 自动转正失败（黑名单 / 信用不足 / 封禁 / 时段冲突 / 未读手册 / 超额）：降级为限时资格
                    w.setStatus(Waitlist.NOTIFIED);
                    w.setNotifiedTime(LocalDateTime.now());
                    waitlistMapper.updateById(w);
                    int hours = sysConfigService.getInt(SysConfigService.KEY_WAITLIST_EXPIRE_HOURS);
                    messageService.push(w.getUserId(), "候补资格已发放",
                            "您候补的设备时段有空位释放，已获得限时预约资格，请在 " + hours
                                    + " 小时内尽快提交该时段的预约，逾期资格将顺延给下一位。",
                            Message.TYPE_RESERVATION);
                }
                break; // 只处理队首一位
            }
        }
    }

    /**
     * 候补自动转正：为队首用户直接创建该时段的待审批预约。
     * 全部前置校验通过才创建（预约资格与人工提交完全一致）；任一不满足返回 false，由调用方降级通知。
     */
    private boolean tryAutoPromote(Waitlist w) {
        User user = userMapper.selectById(w.getUserId());
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) return false;
        if (user.getBlacklisted() != null && user.getBlacklisted() == 1) return false;
        int creditScore = user.getCreditScore() == null ? 100 : user.getCreditScore();
        if (creditScore < sysConfigService.getInt(SysConfigService.KEY_CREDIT_THRESHOLD)) return false;
        if (cancelBanService.stats(w.getUserId()).isBanned()) return false;
        Equipment equipment = equipmentMapper.selectById(w.getEquipmentId());
        if (equipment == null || !Equipment.AVAILABLE.equals(equipment.getStatus())) return false;
        // 手册阅读前置：未读手册不自动创建，降级为限时资格（用户阅读后自行提交）
        if (!manualService.hasRead(w.getUserId(), w.getEquipmentId())) return false;
        // 时段冲突：同设备与有效预约（待审批/借用中/使用中）重叠则不转正
        Long conflict = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getEquipmentId, w.getEquipmentId())
                .in(Reservation::getStatus, Reservation.ACTIVE_STATUSES)
                .lt(Reservation::getStartTime, w.getEndTime())
                .gt(Reservation::getEndTime, w.getStartTime()));
        if (conflict > 0) return false;
        // 单次时长规则（按设备分类上下限）
        long minutes = ChronoUnit.MINUTES.between(w.getStartTime(), w.getEndTime());
        if (minutes < categoryRuleService.minOf(equipment.getCategory())
                || minutes > categoryRuleService.maxOf(equipment.getCategory())) return false;
        // 同时预约数量上限
        int maxActive = sysConfigService.getInt(SysConfigService.KEY_MAX_ACTIVE);
        Long active = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, w.getUserId())
                .in(Reservation::getStatus, Reservation.ACTIVE_STATUSES));
        if (active >= maxActive) return false;

        Reservation r = new Reservation();
        r.setUserId(w.getUserId());
        r.setEquipmentId(w.getEquipmentId());
        r.setStartTime(w.getStartTime());
        r.setEndTime(w.getEndTime());
        r.setStatus(Reservation.PENDING);
        r.setPurpose("【候补自动转正】候补时段空位释放，系统自动创建预约申请");
        reservationMapper.insert(r);

        w.setStatus(Waitlist.PROMOTED);
        w.setReservationId(r.getId());
        waitlistMapper.updateById(w);

        // 双通道通知：站内消息（铃铛未读角标）+ 待审批预约（看板我的预约卡片角标 / 管理员审批角标）
        messageService.push(w.getUserId(), "候补已自动转为正式预约",
                "您候补的设备时段已释放，系统已自动为您创建该时段的预约申请（待审批）。"
                        + "请在【我的预约】中查看详情并留意审批结果。",
                Message.TYPE_RESERVATION);
        opLogService.log("WAITLIST_PROMOTE", "候补 #" + w.getId() + " 自动转为正式预约 #" + r.getId());
        return true;
    }

    /** 我的候补记录（分页） */
    public Page<Waitlist> myPage(Long userId, long page, long size) {
        return waitlistMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Waitlist>()
                        .eq(Waitlist::getUserId, userId)
                        .orderByDesc(Waitlist::getCreateTime));
    }

    /** 用户取消候补 */
    @Transactional
    public void cancel(Long id, Long userId) {
        Waitlist w = waitlistMapper.selectById(id);
        if (w == null || !w.getUserId().equals(userId)) {
            throw new BusinessException("候补记录不存在");
        }
        if (!Waitlist.WAITING.equals(w.getStatus())) {
            throw new BusinessException("当前状态不可取消候补");
        }
        w.setStatus(Waitlist.CANCELLED);
        waitlistMapper.updateById(w);
    }

    /** 排队中数量（看板统计） */
    public long waitingCount() {
        return waitlistMapper.selectCount(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getStatus, Waitlist.WAITING));
    }
}
