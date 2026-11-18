package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("equipment")
public class Equipment {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String UNDER_REPAIR = "UNDER_REPAIR";
    public static final String SCRAPPED = "SCRAPPED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String model;
    private String category;
    private String location;
    private String status;
    private String description;
    /** 标签，逗号分隔：光学仪器/电子测试设备/高温设备/危险化学品配套设备/可外借/仅限实验室使用 */
    private String tags;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 高危标签（安全须知 / 周额度管控依据） */
    public static final String TAG_HIGH_TEMP = "高温设备";
    public static final String TAG_HAZARDCHEM = "危险化学品配套设备";

    public boolean isHighRisk() {
        return tags != null && (tags.contains(TAG_HIGH_TEMP) || tags.contains(TAG_HAZARDCHEM));
    }
}
