package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 设备手册阅读确认记录（学生预约前强制确认阅读） */
@Data
@TableName("manual_read_record")
public class ManualReadRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long equipmentId;
    private String equipmentName;
    private LocalDateTime readTime;
    private String ip;
}
