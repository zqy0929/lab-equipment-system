package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内消息 */
@Data
@TableName("message")
public class Message {
    public static final String TYPE_RESERVATION = "RESERVATION";
    public static final String TYPE_SYSTEM = "SYSTEM";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;        // 接收人
    private String title;
    private String content;
    private String type;
    private Integer isRead;     // 0 未读 1 已读
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
