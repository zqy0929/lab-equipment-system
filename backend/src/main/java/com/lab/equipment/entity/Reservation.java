package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("reservations")
public class Reservation {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String CLAIMED = "CLAIMED";           // 已领用（使用中）
    public static final String REJECTED = "REJECTED";
    public static final String RETURNED = "RETURNED";
    public static final String CANCELLED = "CANCELLED";
    public static final String AUTO_RELEASED = "AUTO_RELEASED"; // 超时未领用自动释放
    public static final String EXPIRED = "EXPIRED";             // 已过期（超过结束时间未完成）

    /** 有效（占用时段/额度）的状态集合 */
    public static final List<String> ACTIVE_STATUSES = List.of(PENDING, APPROVED, CLAIMED);

    /**
     * 计入「单人同时预约设备数量」的状态：
     * PENDING（待审批）、APPROVED（借用中，已通过待领用）、CLAIMED（使用中，未归还）。
     * 已驳回 / 已取消 / 已过期 / 自动释放 / 已归还均不计数。
     */
    public static final List<String> QUOTA_STATUSES = List.of(PENDING, APPROVED, CLAIMED);

    /** 终态（不再占用时段、不可再取消/改时间） */
    public static final List<String> FINAL_STATUSES = List.of(REJECTED, RETURNED, CANCELLED, AUTO_RELEASED, EXPIRED);

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    @TableField(exist = false)
    private String username;      // 关联查询展示
    private Long equipmentId;
    @TableField(exist = false)
    private String equipmentName;
    @TableField(exist = false)
    private String equipmentModel;
    @TableField(exist = false)
    private String equipmentLocation;
    @TableField(exist = false)
    private String equipmentTags;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String purpose;
    private String status;
    private String rejectReason;
    private Long approverId;
    private String approverName;
    private Integer remindSent;   // 即将开始提醒是否已发送 0/1
    private LocalDateTime claimTime;    // 领用确认时间
    private String claimPhoto;          // 领用照片（借用前状态）
    private LocalDateTime returnTime;   // 归还时间
    private String returnPhoto;         // 归还照片
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
