package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 高危设备安全须知阅读记录 */
@Data
@TableName("safety_read_record")
public class SafetyReadRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    @TableField(exist = false)
    private String username;
    private Long equipmentId;
    @TableField(exist = false)
    private String equipmentName;
    private Long reservationId;
    private String ip;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
