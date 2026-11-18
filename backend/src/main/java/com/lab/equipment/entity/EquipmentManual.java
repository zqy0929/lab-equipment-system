package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 设备操作手册：每台设备一份（操作文档 + 安全注意事项 + 手册附件） */
@Data
@TableName("equipment_manual")
public class EquipmentManual {
    public static final String TYPE_OPERATION = "操作文档";
    public static final String TYPE_SAFETY = "安全注意事项";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long equipmentId;
    private String content;         // 操作文档
    private String safetyNotes;     // 安全注意事项
    private String attachmentName;  // 手册附件原始文件名
    private String attachmentPath;  // 附件存储路径
    private String attachmentType;  // 附件扩展名
    private Long editorId;
    private String editorName;
    @TableField(exist = false)
    private String equipmentName;   // 关联展示
    @TableField(exist = false)
    private String equipmentModel;
    @TableField(exist = false)
    private String equipmentCategory;
    @TableField(exist = false)
    private String equipmentLocation;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
