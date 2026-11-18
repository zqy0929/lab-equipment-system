package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 预约取消记录：用户主动取消时按距开始时长写入，weight 为计入 30 天统计的次数 */
@Data
@TableName("cancel_record")
public class CancelRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long reservationId;
    private LocalDateTime cancelTime;
    /** 计入次数：开始前 24h~3h 取消 = 1；3h 内取消 = 2；24h 以上不写记录 */
    private Integer weight;
    /** 计次说明（如：开始前 2.5 小时取消，计 2 次） */
    private String detail;

    @TableField(exist = false)
    private String username;      // 关联查询展示
    @TableField(exist = false)
    private String realName;
    @TableField(exist = false)
    private String role;
    @TableField(exist = false)
    private String equipmentName;
}
