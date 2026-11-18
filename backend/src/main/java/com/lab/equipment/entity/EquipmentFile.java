package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 设备资料库附件：每台设备独立管理，互不可见 */
@Data
@TableName("equipment_file")
public class EquipmentFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long equipmentId;
    private String fileName;    // 原始文件名
    private String filePath;    // 存储路径
    private Long fileSize;
    private String fileType;
    private Long uploaderId;
    @TableField(exist = false)
    private String uploaderName; // 关联查询展示
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
