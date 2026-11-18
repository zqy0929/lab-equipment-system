package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.dto.ReservationCreateDTO;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.entity.Message;
import com.lab.equipment.entity.Reservation;
import com.lab.equipment.entity.SafetyReadRecord;
import com.lab.equipment.entity.User;
import com.lab.equipment.entity.ViolationRecord;
import com.lab.equipment.mapper.EquipmentMapper;
import com.lab.equipment.mapper.ReservationMapper;
import com.lab.equipment.mapper.SafetyReadRecordMapper;
import com.lab.equipment.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    /** 超出同时预约数量上限时的统一提示（前后端一致） */
    public static final String QUOTA_LIMIT_MESSAGE =
            "您已达到同时预约设备数量上限，请等待已有预约审批结束或者归还设备后再预约";


    private final ReservationMapper reservationMapper;
    private final EquipmentMapper equipmentMapper;
    private final UserMapper userMapper;
    private final SysConfigService sysConfigService;
    private final CreditService creditService;
    private final MessageService messageService;
    private final WaitlistService waitlistService;
    private final SafetyReadRecordMapper safetyReadRecordMapper;
    private final OpLogService opLogService;
    private final CategoryRuleService categoryRuleService;
    private final ManualService manualService;
    private final com.lab.equipment.service.CancelBanService cancelBanService;

    /** 创建预约（黑名单/额度/高危须知/规则/信用/冲突校验 + 审计 + 消息推送） */
    @Transactional
    public Reservation create(ReservationCreateDTO dto) {
        return createInternal(dto, true);
    }

    /** 教师 / 管理员批量代提交：被代提交学生的阅读确认由教师统一履行，不再逐人拦截 */
    @Transactional
    public Reservation createOnBehalf(ReservationCreateDTO dto) {
        return createInternal(dto, false);
    }

    private Reservation createInternal(ReservationCreateDTO dto, boolean enforceManualRead) {
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        if (dto.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("预约时间不能早于当前时间");
        }
        // ---- 预约规则校验（系统配置） ----
        int advanceDays = sysConfigService.getInt(SysConfigService.KEY_ADVANCE_DAYS);
        if (dto.getStartTime().toLocalDate()
                .isAfter(LocalDate.now().plusDays(advanceDays))) {
            throw new BusinessException("最多可提前 " + advanceDays + " 天预约");
        }

        Equipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
        if (equipment == null) throw new BusinessException("设备不存在");
        if (!Equipment.AVAILABLE.equals(equipment.getStatus())) {
            throw new BusinessException("该设备当前不可预约（维修中或已报废）");
        }

        // ---- 单次时长校验：按设备所属分类的规则判定 ----
        long durationMinutes = ChronoUnit.MINUTES.between(dto.getStartTime(), dto.getEndTime());
        String category = equipment.getCategory();
        int minDuration = categoryRuleService.minOf(category);
        int maxDuration = categoryRuleService.maxOf(category);
        String categoryLabel = (category == null || category.isBlank()) ? "该设备" : "「" + category + "」类设备";
        if (durationMinutes < minDuration) {
            throw new BusinessException(categoryLabel + "单次预约时长不得少于 " + minDuration + " 分钟");
        }
        if (durationMinutes > maxDuration) {
            throw new BusinessException(categoryLabel + "单次预约时长不得超过 " + maxDuration + " 分钟");
        }

        User user = UserContext.get();
        // ---- 黑名单拦截 ----
        if (user.getBlacklisted() != null && user.getBlacklisted() == 1) {
            throw new BusinessException("您已被列入预约黑名单，暂时无法发起预约，请联系管理员");
        }
        // ---- 取消超限封禁校验（30 天内取消计次超过阈值，禁止提交新预约） ----
        cancelBanService.ensureCanCreate(user);
        // ---- 信用分校验 ----
        creditService.ensureCanReserve(user);

        // ---- 设备手册阅读确认（学生本人预约前必须阅读并确认） ----
        if (enforceManualRead && "STUDENT".equals(user.getRole())
                && manualService.hasReadableContent(equipment.getId())
                && !manualService.hasRead(user.getId(), equipment.getId())) {
            throw new BusinessException("请先阅读《" + equipment.getName() + "》设备手册并勾选确认后再提交预约");
        }

        // ---- 额度管控：同时持有的有效预约数上限（待审批 + 借用中 + 使用中）----
        if (quota(user.getId()).atLimit()) {
            throw new BusinessException(QUOTA_LIMIT_MESSAGE);
        }

        boolean highRisk = equipment.isHighRisk();
        // ---- 高危设备：必须勾选安全须知，并记录阅读 ----
        if (highRisk) {
            if (dto.getSafetyAcknowledged() == null || !dto.getSafetyAcknowledged()) {
                throw new BusinessException("该设备为高危设备，请先阅读并勾选《安全须知》后再提交预约");
            }
            // 每周高危设备预约次数上限（周一为一周开始）
            int weeklyLimit = sysConfigService.getInt(SysConfigService.KEY_WEEKLY_HIGHRISK);
            LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
            LocalDateTime weekEnd = weekStart.plusWeeks(1);
            long weeklyCount = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getUserId, user.getId())
                    .in(Reservation::getStatus, List.of(Reservation.PENDING, Reservation.APPROVED,
                            Reservation.CLAIMED, Reservation.RETURNED, Reservation.AUTO_RELEASED))
                    .inSql(Reservation::getEquipmentId,
                            "SELECT id FROM equipment WHERE tags LIKE '%高温设备%' OR tags LIKE '%危险化学品配套设备%'")
                    .ge(Reservation::getStartTime, weekStart)
                    .lt(Reservation::getStartTime, weekEnd));
            if (weeklyCount >= weeklyLimit) {
                throw new BusinessException("本周高危设备预约次数已达上限（" + weeklyLimit + " 次/周）");
            }
        }

        // 冲突检测：仅【同一台设备】在同一时间段互斥；不同设备不做时间互斥，
        // 允许用户同一时间段预约多台不同设备，满足同步开展实验的需求
        Long conflict = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getEquipmentId, dto.getEquipmentId())
                .in(Reservation::getStatus, Reservation.ACTIVE_STATUSES)
                .lt(Reservation::getStartTime, dto.getEndTime())
                .gt(Reservation::getEndTime, dto.getStartTime()));
        if (conflict > 0) {
            throw new BusinessException("该设备在此时间段已被预约或存在待审批的预约，请更换时间或加入候补队列");
        }

        Reservation r = new Reservation();
        r.setUserId(user.getId());
        r.setEquipmentId(dto.getEquipmentId());
        r.setStartTime(dto.getStartTime());
        r.setEndTime(dto.getEndTime());
        r.setPurpose(dto.getPurpose());
        r.setStatus(Reservation.PENDING);
        r.setRemindSent(0);
        reservationMapper.insert(r);

        // 高危须知阅读记录（含 IP，供管理员查询）
        if (highRisk) {
            SafetyReadRecord record = new SafetyReadRecord();
            record.setUserId(user.getId());
            record.setEquipmentId(equipment.getId());
            record.setReservationId(r.getId());
            record.setIp(OpLogService.currentIp());
            safetyReadRecordMapper.insert(record);
        }

        // 审计日志
        opLogService.log("CREATE_RESERVATION",
                "提交预约：设备「" + equipment.getName() + "」 " + r.getStartTime() + " ~ " + r.getEndTime()
                        + (highRisk ? "（高危设备，已阅读安全须知）" : ""));

        // 站内消息：预约提交成功
        messageService.push(user.getId(), "预约提交成功",
                "您对「" + equipment.getName() + "」的预约已提交（"
                        + r.getStartTime().toLocalDate() + " "
                        + r.getStartTime().toLocalTime() + " ~ "
                        + r.getEndTime().toLocalTime() + "），等待审批。",
                Message.TYPE_RESERVATION);
        return r;
    }

    /** 修改预约时段（仅待审批状态，本人操作；校验冲突并写入审计日志） */
    @Transactional
    public void modify(Long id, Long userId, LocalDateTime start, LocalDateTime end) {
        Reservation r = requireReservation(id);
        if (!r.getUserId().equals(userId)) {
            throw new BusinessException("只能修改自己的预约");
        }
        if (!Reservation.PENDING.equals(r.getStatus())) {
            throw new BusinessException("仅待审批状态的预约可以修改时段；已通过审批或已领用的预约不可修改");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException("请选择有效的时间段");
        }
        if (end.isBefore(LocalDateTime.now())) {
            throw new BusinessException("预约时间不能早于当前时间");
        }
        // 与新时段规则校验（按该设备所属分类的时长规则）
        long durationMinutes = ChronoUnit.MINUTES.between(start, end);
        Equipment device = equipmentMapper.selectById(r.getEquipmentId());
        String category = device == null ? null : device.getCategory();
        int minDuration = categoryRuleService.minOf(category);
        int maxDuration = categoryRuleService.maxOf(category);
        String categoryLabel = (category == null || category.isBlank()) ? "该设备" : "「" + category + "」类设备";
        if (durationMinutes < minDuration || durationMinutes > maxDuration) {
            throw new BusinessException(categoryLabel + "单次预约时长须在 " + minDuration
                    + " - " + maxDuration + " 分钟之间");
        }
        // 新时段冲突检测（排除自身）
        Long conflict = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getEquipmentId, r.getEquipmentId())
                .ne(Reservation::getId, r.getId())
                .in(Reservation::getStatus, Reservation.ACTIVE_STATUSES)
                .lt(Reservation::getStartTime, end)
                .gt(Reservation::getEndTime, start));
        if (conflict > 0) {
            throw new BusinessException("新时间段与其他预约冲突，请更换时间");
        }
        String before = r.getStartTime() + " ~ " + r.getEndTime();
        r.setStartTime(start);
        r.setEndTime(end);
        reservationMapper.updateById(r);
        Equipment equipment = equipmentMapper.selectById(r.getEquipmentId());
        opLogService.log("MODIFY_RESERVATION",
                "修改预约时段：设备「" + (equipment != null ? equipment.getName() : r.getEquipmentId())
                        + "」由 " + before + " 修改为 " + start + " ~ " + end);
        messageService.push(userId, "预约时段修改成功",
                "您对「" + (equipment != null ? equipment.getName() : "设备") + "」的预约时段已修改为 "
                        + start.toLocalDate() + " " + start.toLocalTime() + " ~ " + end.toLocalTime()
                        + "，等待重新审批。",
                Message.TYPE_RESERVATION);
    }

    /** 领用确认：预约开始后的领用窗口内点击，可上传现场照片 */
    @Transactional
    public void claim(Long id, Long userId, String photoPath) {
        Reservation r = requireReservation(id);
        if (!r.getUserId().equals(userId)) {
            throw new BusinessException("只能确认自己的预约");
        }
        if (!Reservation.APPROVED.equals(r.getStatus())) {
            throw new BusinessException("仅审批通过的预约可进行领用确认");
        }
        int timeout = sysConfigService.getInt(SysConfigService.KEY_CLAIM_TIMEOUT);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(r.getStartTime())) {
            throw new BusinessException("预约尚未开始，开始后可进行领用确认");
        }
        if (now.isAfter(r.getStartTime().plusMinutes(timeout))) {
            throw new BusinessException("已超过领用确认时间（开始后 " + timeout + " 分钟），预约可能已被自动释放");
        }
        r.setStatus(Reservation.CLAIMED);
        r.setClaimTime(now);
        if (StringUtils.hasText(photoPath)) r.setClaimPhoto(photoPath);
        reservationMapper.updateById(r);
        opLogService.log("CLAIM_RESERVATION", "领用确认：预约 #" + r.getId());
    }

    /** 归还设备（本人或管理员/保管员），可上传归还照片供核验 */
    @Transactional
    public void returnDevice(Long id, User user, String photoPath) {
        Reservation r = requireReservation(id);
        boolean own = r.getUserId().equals(user.getId());
        boolean manager = "ADMIN".equals(user.getRole()) || "KEEPER".equals(user.getRole());
        if (!own && !manager) {
            throw new BusinessException("无权限操作该预约");
        }
        if (!Reservation.APPROVED.equals(r.getStatus()) && !Reservation.CLAIMED.equals(r.getStatus())) {
            throw new BusinessException("仅借用中/使用中的预约可归还");
        }
        r.setStatus(Reservation.RETURNED);
        r.setReturnTime(LocalDateTime.now());
        if (StringUtils.hasText(photoPath)) r.setReturnPhoto(photoPath);
        reservationMapper.updateById(r);
        opLogService.log("RETURN_RESERVATION", "归还设备：预约 #" + r.getId());
    }

    /**
     * 自动释放：预约开始后 X 分钟内未领用确认的，自动取消并释放时段。
     * 由定时任务调用；返回被释放的记录数。
     */
    @Transactional
    public int autoReleaseExpired() {
        int timeout = sysConfigService.getInt(SysConfigService.KEY_CLAIM_TIMEOUT);
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeout);
        List<Reservation> expired = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, Reservation.APPROVED)
                .le(Reservation::getStartTime, deadline)
                // 仅释放尚未结束的时段；已过结束时间的由 autoExpireReservations 统一标记【已过期】
                .ge(Reservation::getEndTime, LocalDateTime.now()));
        for (Reservation r : expired) {
            r.setStatus(Reservation.AUTO_RELEASED);
            reservationMapper.updateById(r);
            Equipment equipment = equipmentMapper.selectById(r.getEquipmentId());
            String equipName = equipment != null ? equipment.getName() : "设备";
            messageService.push(r.getUserId(), "预约已自动释放",
                    "您对「" + equipName + "」的预约（" + r.getStartTime().toLocalDate() + " "
                            + r.getStartTime().toLocalTime() + "）因开始后 " + timeout
                            + " 分钟内未进行领用确认，已被系统自动取消释放。",
                    Message.TYPE_RESERVATION);
            opLogService.log("AUTO_RELEASE", "自动释放预约 #" + r.getId()
                    + "（设备「" + equipName + "」，超时未领用）");
            // 释放的时段通知候补队首
            waitlistService.notifyOnSlotFreed(r.getEquipmentId(), r.getStartTime(), r.getEndTime());
        }
        return expired.size();
    }

    /**
     * 过期预约自动处理：当前时间晚于【结束时间】仍未完成的有效预约 → 置为【已过期】。
     * <ul>
     *   <li>APPROVED（已通过未领用）过期：判违约（爽约），扣信用分</li>
     *   <li>CLAIMED（使用中未归还）过期：判违约（超时归还），扣信用分</li>
     *   <li>PENDING（待审批）过期：仅置过期，不扣分</li>
     * </ul>
     * 过期后时段自动释放给他人预约，并通知候补队首；记录不删除，保留在预约列表与审计日志中。
     */
    @Transactional
    public int autoExpireReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> overdue = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .in(Reservation::getStatus, Reservation.PENDING, Reservation.APPROVED, Reservation.CLAIMED)
                .lt(Reservation::getEndTime, now));
        if (overdue.isEmpty()) return 0;
        int deduction = sysConfigService.getInt(SysConfigService.KEY_EXPIRE_DEDUCTION);
        for (Reservation r : overdue) {
            String from = r.getStatus();
            r.setStatus(Reservation.EXPIRED);
            reservationMapper.updateById(r);

            Equipment equipment = equipmentMapper.selectById(r.getEquipmentId());
            String equipName = equipment != null ? equipment.getName() : "设备";
            String slot = r.getStartTime().toLocalDate() + " " + r.getStartTime().toLocalTime()
                    + " ~ " + r.getEndTime().toLocalTime();

            // 归属违约：已通过未领用 / 使用中未归还 → 违约记录 + 扣分（手动取消不会走到这里）
            if (Reservation.APPROVED.equals(from) || Reservation.CLAIMED.equals(from)) {
                boolean noShow = Reservation.APPROVED.equals(from);
                String remark = noShow
                        ? "预约「" + equipName + "」(" + slot + ") 已通过但未按时使用，自动过期"
                        : "预约「" + equipName + "」(" + slot + ") 已领用但未按时归还，自动过期";
                creditService.autoRegisterViolation(r.getUserId(), r.getId(),
                        noShow ? ViolationRecord.NO_SHOW : ViolationRecord.OVERDUE, deduction, remark);
                messageService.push(r.getUserId(), "预约已过期",
                        "您对「" + equipName + "」的预约（" + slot + "）已超过结束时间，系统已标记为【已过期】。"
                                + "该时段已释放给其他用户，并按违约规则扣除信用分 " + deduction + " 分。",
                        Message.TYPE_RESERVATION);
            } else {
                messageService.push(r.getUserId(), "预约已过期",
                        "您对「" + equipName + "」的预约（" + slot + "）已超过结束时间且未获审批，系统已标记为【已过期】。",
                        Message.TYPE_RESERVATION);
            }

            opLogService.log("EXPIRE_RESERVATION", "预约过期 #" + r.getId() + "（设备「" + equipName
                    + "」，原状态 " + from + "，已释放时段）");
            // 资源释放：时段可被他人预约；通知候补队首
            waitlistService.notifyOnSlotFreed(r.getEquipmentId(), r.getStartTime(), r.getEndTime());
        }
        return overdue.size();
    }

    /**
     * 当前用户的同时预约额度情况。
     * 只统计状态为【待审批、借用中（含使用中未归还）】的预约；已驳回 / 已取消 / 已过期 / 已归还 / 自动释放不计数。
     * 上限取系统配置 max_active_reservations（默认 2 台）。
     */
    public QuotaInfo quota(Long userId) {
        int maxActive = Math.max(1, sysConfigService.getInt(SysConfigService.KEY_MAX_ACTIVE));
        long activeCount = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, Reservation.QUOTA_STATUSES));
        long remaining = Math.max(0, maxActive - activeCount);
        return new QuotaInfo(maxActive, activeCount, remaining, activeCount >= maxActive);
    }

    /** 我的预约（分页） */
    public Page<Reservation> myPage(Long userId, String status, long page, long size) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.eq("r.user_id", userId);
        if (StringUtils.hasText(status)) wrapper.eq("r.status", status);
        wrapper.orderByDesc("r.create_time");
        return reservationMapper.selectWithDetailPage(new Page<>(page, size), wrapper);
    }

    /** 管理员审批列表（分页） */
    public Page<Reservation> adminPage(String status, long page, long size) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) wrapper.eq("r.status", status);
        wrapper.orderByAsc("r.status").orderByAsc("r.start_time");
        return reservationMapper.selectWithDetailPage(new Page<>(page, size), wrapper);
    }

    /** 预约日历数据：某月内所有有效预约；onlyUserId 非空时仅返回该用户的预约（学生角色强制仅见本人） */
    /** 预约日历数据：userIds 为 null 表示全部可见，否则仅返回该用户集合的预约（课题组共享域，后端强制） */
    public List<Reservation> calendar(String month, List<Long> userIds) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException("月份格式错误，应为 yyyy-MM");
        }
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        if (userIds != null) wrapper.in("r.user_id", userIds);
        wrapper.in("r.status", Reservation.PENDING, Reservation.APPROVED, Reservation.CLAIMED, Reservation.EXPIRED)
                .lt("r.start_time", end)
                .gt("r.end_time", start);
        return reservationMapper.selectWithDetailList(wrapper);
    }

    /** 审批（管理员/教师），通过/驳回后站内消息通知预约人 */
    @Transactional
    public void approve(ApprovalContext ctx) {
        Reservation r = requireReservation(ctx.id());
        if (!Reservation.PENDING.equals(r.getStatus())) {
            throw new BusinessException("该预约不在待审批状态");
        }
        // 权限隔离：管理员/教师可提交自己的预约，但不得审批自己提交的预约
        if (ctx.admin() != null && r.getUserId() != null
                && r.getUserId().equals(ctx.admin().getId())) {
            throw new BusinessException("不能审批自己提交的预约，请由其他管理员或实验室老师审批");
        }
        if (ctx.approved() && !StringUtils.hasText(ctx.reason())) {
            ctx = new ApprovalContext(ctx.id(), true, "同意", ctx.admin());
        }
        if (!ctx.approved() && !StringUtils.hasText(ctx.reason())) {
            throw new BusinessException("驳回时请填写原因");
        }
        r.setStatus(ctx.approved() ? Reservation.APPROVED : Reservation.REJECTED);
        r.setRejectReason(ctx.approved() ? null : ctx.reason());
        r.setApproverId(ctx.admin().getId());
        r.setApproverName(StringUtils.hasText(ctx.admin().getRealName()) ? ctx.admin().getRealName() : ctx.admin().getUsername());
        reservationMapper.updateById(r);

        Equipment equipment = equipmentMapper.selectById(r.getEquipmentId());
        String equipName = equipment != null ? equipment.getName() : "设备";
        opLogService.log(ctx.approved() ? "APPROVE_RESERVATION" : "REJECT_RESERVATION",
                (ctx.approved() ? "审批通过" : "驳回预约") + "：预约 #" + r.getId()
                        + "，设备「" + equipName + "」" + (ctx.approved() ? "" : "，理由：" + ctx.reason()));
        if (ctx.approved()) {
            messageService.push(r.getUserId(), "预约审批通过",
                    "您对「" + equipName + "」的预约（" + r.getStartTime().toLocalDate() + " "
                            + r.getStartTime().toLocalTime() + " ~ " + r.getEndTime().toLocalTime()
                            + "）已通过审批。请在预约开始后 " + sysConfigService.getInt(SysConfigService.KEY_CLAIM_TIMEOUT)
                            + " 分钟内完成领用确认，否则预约将被自动释放。",
                    Message.TYPE_RESERVATION);
        } else {
            messageService.push(r.getUserId(), "预约审批驳回",
                    "您对「" + equipName + "」的预约被驳回。驳回理由：" + ctx.reason(),
                    Message.TYPE_RESERVATION);
            // 驳回同样释放时段：候补队首自动转正（校验不通过降级为限时资格通知）
            waitlistService.notifyOnSlotFreed(r.getEquipmentId(), r.getStartTime(), r.getEndTime());
        }
    }

    /**
     * 用户取消自己的预约（受免费取消截止时间约束）；释放时段并通知候补队首。
     * 返回本次取消计入 30 天统计的次数（0 = 开始前 24 小时以上取消不计入）。
     */
    @Transactional
    public int cancel(Long id, Long userId) {
        Reservation r = requireReservation(id);
        if (!r.getUserId().equals(userId)) {
            throw new BusinessException("只能取消自己的预约");
        }
        if (!Reservation.ACTIVE_STATUSES.contains(r.getStatus())) {
            throw new BusinessException("当前状态不可取消");
        }
        // 免费取消截止：开始前 X 分钟内不可取消
        int cutoff = sysConfigService.getInt(SysConfigService.KEY_CANCEL_CUTOFF);
        if (LocalDateTime.now().isAfter(r.getStartTime().minusMinutes(cutoff))) {
            throw new BusinessException("预约开始前 " + cutoff + " 分钟内不可取消，如特殊情况请联系管理员");
        }
        r.setStatus(Reservation.CANCELLED);
        reservationMapper.updateById(r);

        // 30 天取消统计：按距开始时长写入计次记录（24h 以上不计入，24h~3h 计 1，3h 内计 2）
        int weight = cancelBanService.recordCancel(userId, r.getId(), r.getStartTime());

        // 站内消息：通知管理员有人取消预约
        User operator = UserContext.get();
        String name = StringUtils.hasText(operator.getRealName()) ? operator.getRealName() : operator.getUsername();
        Equipment equipment = equipmentMapper.selectById(r.getEquipmentId());
        String equipName = equipment != null ? equipment.getName() : "设备";
        for (User admin : userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"))) {
            messageService.push(admin.getId(), "预约被取消",
                    "用户 " + name + " 取消了对「" + equipName + "」的预约（"
                            + r.getStartTime().toLocalDate() + " " + r.getStartTime().toLocalTime() + "）。",
                    Message.TYPE_RESERVATION);
        }
        opLogService.log("CANCEL_RESERVATION", "取消预约 #" + r.getId() + "（设备「" + equipName + "」）"
                + (weight > 0 ? "，计入 30 天取消统计 " + weight + " 次" : "，距开始 24 小时以上，不计入取消统计"));
        // 释放的时段通知候补队首
        waitlistService.notifyOnSlotFreed(r.getEquipmentId(), r.getStartTime(), r.getEndTime());
        return weight;
    }

    private Reservation requireReservation(Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) throw new BusinessException("预约记录不存在");
        return r;
    }

    /** 供控制器读取照片归属与路径 */
    public Reservation getById(Long id) {
        return reservationMapper.selectById(id);
    }

    /** 审批参数上下文 */
    public record ApprovalContext(Long id, boolean approved, String reason, User admin) {}

    /** 同时预约额度：上限 / 已占用 / 剩余 / 是否已达上限 */
    public record QuotaInfo(int maxActive, long activeCount, long remaining, boolean atLimit) {}
}
