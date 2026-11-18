package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("damage_records")
public class DamageRecord {
    public static final String REPORTED = "REPORTED";
    public static final String FIXING = "FIXING";
    public static final String FIXED = "FIXED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long equipmentId;
    @TableField(exist = false)
    private String equipmentName;
    private Long reporterId;
    private String reporterName;
    private String description;
    private String severity;     // MINOR / MODERATE / SEVERE
    private String status;       // REPORTED / FIXING / FIXED
    private BigDecimal cost;
    private String handlerName;
    private String handleNote;
    private LocalDateTime handleTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
