package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 候补队列：时段约满后排队，空位释放时通知队首用户 */
@Data
@TableName("waitlist")
public class Waitlist {
    public static final String WAITING = "WAITING";       // 排队中
    public static final String NOTIFIED = "NOTIFIED";     // 已获限时资格
    public static final String FULFILLED = "FULFILLED";   // 已完成预约
    public static final String PROMOTED = "PROMOTED";     // 已自动转为正式预约
    public static final String CANCELLED = "CANCELLED";   // 已取消

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long equipmentId;
    @TableField(exist = false)
    private String equipmentName;
    private Long userId;
    @TableField(exist = false)
    private String username;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    /** 自动转正生成的预约 ID（仅 PROMOTED 状态有值） */
    private Long reservationId;
    private LocalDateTime notifiedTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
