package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 安全隐患上报（全员可上报，安全员处理） */
@Data
@TableName("hazard_report")
public class HazardReport {
    public static final String REPORTED = "REPORTED";
    public static final String HANDLED = "HANDLED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    @TableField(exist = false)
    private String username;
    private Long equipmentId;
    @TableField(exist = false)
    private String equipmentName;
    private String content;
    private String status;
    private String handlerName;
    private String handleNote;
    private LocalDateTime handleTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
