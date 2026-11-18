package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作审计日志：仅写入与查看，禁止修改/删除 */
@Data
@TableName("op_log")
public class OpLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String action;      // 动作标识
    private String detail;      // 操作内容（含修改前后信息）
    private String ip;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
