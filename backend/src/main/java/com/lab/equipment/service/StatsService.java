package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.DamageRecord;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.entity.Reservation;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.DamageRecordMapper;
import com.lab.equipment.mapper.EquipmentMapper;
import com.lab.equipment.mapper.ReservationMapper;
import com.lab.equipment.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板（按角色五档）：
 * - 学生：未加入课题组 → 仅个人统计（SELF）；已加入 → 个人 + 所有已加入组的联合统计（SELF_GROUP）
 * - 教师：未创建课题组 → 仅个人统计（SELF）；已创建 → 可切换 全局 / 名下各课题组（TEACHER_GROUPS）
 * - 安全员（SAFETY）：固定全局统计，重点突出损耗、安全隐患数据
 * - 管理员 / 设备保管员（ALL）：全套全局统计（保管员为唯一审批负责人，可见待审批数）
 * 课题组只共享【预约信息】；损耗、信用积分严格个人隔离。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final EquipmentMapper equipmentMapper;
    private final ReservationMapper reservationMapper;
    private final DamageRecordMapper damageRecordMapper;
    private final UserMapper userMapper;
    private final com.lab.equipment.service.WaitlistService waitlistService;
    private final com.lab.equipment.mapper.HazardReportMapper hazardReportMapper;
    private final GroupService groupService;

    /** 未处理安全隐患数（安全员看板重点） */
    private long hazardPendingCount() {
        return hazardReportMapper.selectCount(new LambdaQueryWrapper<com.lab.equipment.entity.HazardReport>()
                .eq(com.lab.equipment.entity.HazardReport::getStatus, com.lab.equipment.entity.HazardReport.REPORTED));
    }

    /** 预约状态中文名（图表用） */
    private static final Map<String, String> STATUS_LABEL = Map.ofEntries(
            Map.entry(Reservation.PENDING, "待审批"),
            Map.entry(Reservation.APPROVED, "已通过"),
            Map.entry(Reservation.CLAIMED, "使用中"),
            Map.entry(Reservation.REJECTED, "已驳回"),
            Map.entry(Reservation.RETURNED, "已完成"),
            Map.entry(Reservation.CANCELLED, "已取消"),
            Map.entry(Reservation.AUTO_RELEASED, "自动释放"),
            Map.entry(Reservation.EXPIRED, "已过期"));

    public Map<String, Object> dashboard() {
        User currentUser = UserContext.get();
        String role = currentUser == null ? null : currentUser.getRole();
        if ("ADMIN".equals(role) || "KEEPER".equals(role)) {
            return globalDashboard("ALL");
        }
        if ("SAFETY".equals(role)) {
            return globalDashboard("SAFETY");
        }
        if ("TEACHER".equals(role)) {
            List<Map<String, Object>> owned = groupService.ownedGroups(currentUser);
            if (owned.isEmpty()) {
                return selfDashboard(currentUser);
            }
            return teacherGroupsDashboard(currentUser, owned);
        }
        // 学生（含历史 USER）：个人 +（若已加入课题组）所有已加入组的联合统计（可同时多组）
        Map<String, Object> data = selfDashboard(currentUser);
        List<Map<String, Object>> joined = groupService.myGroups(currentUser);
        if (!joined.isEmpty()) {
            data.put("scope", "SELF_GROUP");
            data.put("myGroups", joined);
            // 多组联合用户域：本人 + 各组（成员 + 负责人）去重
            java.util.Set<Long> union = new java.util.LinkedHashSet<>();
            union.add(currentUser.getId());
            for (Map<String, Object> g : joined) {
                union.addAll(groupService.memberIds((Long) g.get("id")));
                Object ownerId = g.get("ownerId");
                if (ownerId != null) union.add((Long) ownerId);
            }
            data.put("groupMemberCount", union.size() - 1); // 不含本人
            List<Long> groupUserIds = new ArrayList<>(union);
            YearMonth current = YearMonth.now();
            data.put("groupReservationTotal", countReservation(groupUserIds, null, null));
            data.put("groupReservationMonth", countReservation(groupUserIds,
                    current.atDay(1).atStartOfDay(), current.plusMonths(1).atDay(1).atStartOfDay()));
            data.put("groupTrend", trendOf(groupUserIds, current));
            data.put("groupStatusStats", statusStatsOf(groupUserIds));
            data.put("groupStudentTop", studentTop(groupUserIds));
            data.put("groupRecent", recentOf(groupUserIds));
        }
        return data;
    }

    /** 课题组统计的用户域：成员 + 负责人 */
    private List<Long> groupUserIdsOf(Long groupId, User owner) {
        List<Long> ids = new ArrayList<>(groupService.memberIds(groupId));
        if (!ids.contains(owner.getId())) ids.add(owner.getId());
        return ids;
    }

    /* ================= 教师：全局 / 名下各课题组 切换 ================= */
    private Map<String, Object> teacherGroupsDashboard(User me, List<Map<String, Object>> owned) {
        Map<String, Object> data = globalDashboard("TEACHER_GROUPS");
        // 教师无审批权限：不返回待审批数
        data.put("pendingApprovals", null);
        data.put("myReservationTotal", countReservation(me.getId(), null, null));
        YearMonth current = YearMonth.now();
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map<String, Object> g : owned) {
            Long gid = (Long) g.get("id");
            List<Long> userIds = groupUserIdsOf(gid, me);
            Map<String, Object> item = new HashMap<>(g);
            item.put("reservationTotal", countReservation(userIds, null, null));
            item.put("reservationMonth", countReservation(userIds,
                    current.atDay(1).atStartOfDay(), current.plusMonths(1).atDay(1).atStartOfDay()));
            item.put("trend", trendOf(userIds, current));
            item.put("statusStats", statusStatsOf(userIds));
            item.put("studentTop", studentTop(userIds));
            item.put("recent", recentOf(userIds));
            groups.add(item);
        }
        data.put("myGroups", groups);
        return data;
    }

    /* ================= 学生：个人看板 ================= */
    private Map<String, Object> selfDashboard(User me) {
        Map<String, Object> data = new HashMap<>();
        data.put("scope", "SELF");

        data.put("myReservationTotal", countReservation(me.getId(), null, null));
        // 待审批数量（看板「本人预约次数」卡片角标，含候补自动转正生成的申请）
        data.put("myPendingCount", reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, me.getId())
                .eq(Reservation::getStatus, Reservation.PENDING)));
        YearMonth current = YearMonth.now();
        data.put("myReservationMonth", countReservation(me.getId(),
                current.atDay(1).atStartOfDay(), current.plusMonths(1).atDay(1).atStartOfDay()));
        data.put("creditScore", me.getCreditScore() == null ? 100 : me.getCreditScore());
        data.put("myDamageTotal", damageRecordMapper.selectCount(
                new LambdaQueryWrapper<DamageRecord>().eq(DamageRecord::getReporterId, me.getId())));

        // 个人近 6 个月预约趋势
        data.put("myTrend", trendOf(List.of(me.getId()), current));
        // 个人预约状态分布
        data.put("myStatusStats", statusStatsOf(List.of(me.getId())));
        // 个人最近 5 条预约
        QueryWrapper<Reservation> recentWrapper = new QueryWrapper<>();
        recentWrapper.eq("r.user_id", me.getId()).orderByDesc("r.create_time").last("LIMIT 5");
        data.put("myRecent", reservationMapper.selectWithDetailList(recentWrapper));
        return data;
    }

    /* ================= 管理员 / 保管员：全局看板（安全员复用，前端按 scope 突出损耗隐患） ================= */
    private Map<String, Object> globalDashboard(String scope) {
        Map<String, Object> data = new HashMap<>();
        data.put("scope", scope);

        // 设备统计
        data.put("equipmentTotal", equipmentMapper.selectCount(null));
        data.put("equipmentAvailable", equipmentMapper.selectCount(
                new LambdaQueryWrapper<Equipment>().eq(Equipment::getStatus, Equipment.AVAILABLE)));
        data.put("equipmentRepairing", equipmentMapper.selectCount(
                new LambdaQueryWrapper<Equipment>().eq(Equipment::getStatus, Equipment.UNDER_REPAIR)));

        // 预约统计：待审批数量仅对具备审批权限的角色（管理员 / 设备保管员）返回
        User currentUser = UserContext.get();
        boolean canApprove = currentUser != null
                && ("ADMIN".equals(currentUser.getRole()) || "KEEPER".equals(currentUser.getRole()));
        data.put("pendingApprovals", canApprove
                ? reservationMapper.selectCount(
                        new LambdaQueryWrapper<Reservation>().eq(Reservation::getStatus, Reservation.PENDING))
                : null);
        YearMonth current = YearMonth.now();
        data.put("monthReservations", reservationMapper.selectCount(
                new LambdaQueryWrapper<Reservation>()
                        .ge(Reservation::getCreateTime, current.atDay(1).atStartOfDay())
                        .lt(Reservation::getCreateTime, current.plusMonths(1).atDay(1).atStartOfDay())));

        // 损耗统计：按状态分别计数，与损耗记录页筛选标签一一对应（REPORTED/FIXING/FIXED）
        data.put("damageReported", damageRecordMapper.selectCount(
                new LambdaQueryWrapper<DamageRecord>().eq(DamageRecord::getStatus, DamageRecord.REPORTED)));
        data.put("damageFixing", damageRecordMapper.selectCount(
                new LambdaQueryWrapper<DamageRecord>().eq(DamageRecord::getStatus, DamageRecord.FIXING)));
        data.put("damageFixed", damageRecordMapper.selectCount(
                new LambdaQueryWrapper<DamageRecord>().eq(DamageRecord::getStatus, DamageRecord.FIXED)));
        // 未修复总数 = 待处理 + 维修中（兼容旧字段名）
        data.put("damageUnfixed", damageRecordMapper.selectCount(
                new LambdaQueryWrapper<DamageRecord>().in(DamageRecord::getStatus, DamageRecord.REPORTED, DamageRecord.FIXING)));
        // 损耗状态分布（安全员看板重点图表 / 管理员参考）
        data.put("damageStatusStats", List.of(
                Map.of("name", "待处理", "value", data.get("damageReported")),
                Map.of("name", "维修中", "value", data.get("damageFixing")),
                Map.of("name", "已修复", "value", data.get("damageFixed"))));
        // 未处理安全隐患数（安全员重点）
        data.put("hazardPending", hazardPendingCount());

        // 分类分布
        QueryWrapper<Equipment> catWrapper = new QueryWrapper<>();
        catWrapper.select("category AS name", "COUNT(*) AS value")
                .isNotNull("category").groupBy("category");
        data.put("categoryStats", equipmentMapper.selectMaps(catWrapper));

        // 近 6 个月预约趋势
        data.put("monthTrend", trendAll(current));

        // 高危设备使用统计：本月高危设备预约次数 + TOP5 设备
        String highRiskSql = "SELECT id FROM equipment WHERE tags LIKE '%高温设备%' OR tags LIKE '%危险化学品配套设备%'";
        data.put("highRiskMonth", reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .inSql(Reservation::getEquipmentId, highRiskSql)
                .ge(Reservation::getStartTime, current.atDay(1).atStartOfDay())
                .lt(Reservation::getStartTime, current.plusMonths(1).atDay(1).atStartOfDay())));
        QueryWrapper<Reservation> hrWrapper = new QueryWrapper<>();
        hrWrapper.select("equipment_id", "COUNT(*) AS value")
                .inSql("equipment_id", highRiskSql)
                .ge("start_time", current.atDay(1).atStartOfDay())
                .lt("start_time", current.plusMonths(1).atDay(1).atStartOfDay())
                .groupBy("equipment_id");
        List<Map<String, Object>> hrTop = new ArrayList<>();
        for (Map<String, Object> row : reservationMapper.selectMaps(hrWrapper)) {
            Long eqId = Long.valueOf(String.valueOf(row.get("equipment_id")));
            Equipment eq = equipmentMapper.selectById(eqId);
            Map<String, Object> item = new HashMap<>();
            item.put("equipment_name", eq != null ? eq.getName() : "未知设备");
            item.put("value", row.get("value"));
            hrTop.add(item);
        }
        data.put("highRiskTop", hrTop);

        // 候补统计：当前排队数 + 本月已通知资格数
        data.put("waitlistWaiting", waitlistService.waitingCount());

        // 最近 5 条预约
        QueryWrapper<Reservation> recentWrapper = new QueryWrapper<>();
        recentWrapper.orderByDesc("r.create_time").last("LIMIT 5");
        data.put("recentReservations", reservationMapper.selectWithDetailList(recentWrapper));

        return data;
    }

    /* ================= 通用统计工具 ================= */

    private long countReservation(List<Long> userIds, LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<Reservation> w = new LambdaQueryWrapper<>();
        w.in(Reservation::getUserId, userIds);
        if (from != null) w.ge(Reservation::getCreateTime, from);
        if (to != null) w.lt(Reservation::getCreateTime, to);
        return reservationMapper.selectCount(w);
    }

    private long countReservation(Long userId, LocalDateTime from, LocalDateTime to) {
        return countReservation(List.of(userId), from, to);
    }

    /** 近 6 个月预约趋势（限定用户域） */
    private List<Map<String, Object>> trendOf(List<Long> userIds, YearMonth current) {
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            long count = countReservation(userIds,
                    ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay());
            Map<String, Object> item = new HashMap<>();
            item.put("month", ym.format(fmt));
            item.put("count", count);
            trend.add(item);
        }
        return trend;
    }

    /** 全局近 6 个月趋势（原逻辑抽出复用） */
    private List<Map<String, Object>> trendAll(YearMonth current) {
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            Long count = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                    .ge(Reservation::getCreateTime, ym.atDay(1).atStartOfDay())
                    .lt(Reservation::getCreateTime, ym.plusMonths(1).atDay(1).atStartOfDay()));
            Map<String, Object> item = new HashMap<>();
            item.put("month", ym.format(fmt));
            item.put("count", count);
            trend.add(item);
        }
        return trend;
    }

    /** 预约状态分布（限定用户域，按状态分组计数） */
    private List<Map<String, Object>> statusStatsOf(List<Long> userIds) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.select("status AS name", "COUNT(*) AS value")
                .in("user_id", userIds)
                .groupBy("status");
        List<Map<String, Object>> rows = reservationMapper.selectMaps(wrapper);
        rows.forEach(r -> r.put("name", STATUS_LABEL.getOrDefault(String.valueOf(r.get("name")), String.valueOf(r.get("name")))));
        return rows;
    }

    /** 本组学生预约次数 TOP5 */
    private List<Map<String, Object>> studentTop(List<Long> userIds) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.select("user_id", "COUNT(*) AS value")
                .in("user_id", userIds)
                .groupBy("user_id")
                .orderByDesc("value")
                .last("LIMIT 5");
        List<Map<String, Object>> rows = reservationMapper.selectMaps(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long uid = Long.valueOf(String.valueOf(row.get("user_id")));
            User u = userMapper.selectById(uid);
            Map<String, Object> item = new HashMap<>();
            item.put("name", u != null && u.getRealName() != null && !u.getRealName().isBlank()
                    ? u.getRealName() : (u != null ? u.getUsername() : "用户#" + uid));
            item.put("value", row.get("value"));
            result.add(item);
        }
        return result;
    }

    /** 用户域内最近 5 条预约 */
    private List<Reservation> recentOf(List<Long> userIds) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.in("r.user_id", userIds).orderByDesc("r.create_time").last("LIMIT 5");
        return reservationMapper.selectWithDetailList(wrapper);
    }
}
