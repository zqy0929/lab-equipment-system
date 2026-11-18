package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 预约封禁解除记录：管理员/保管员手动解除用户的取消超限封禁 */
@Data
@TableName("ban_lift")
public class BanLift {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDateTime liftTime;
    private Long operatorId;
    private String operatorName;
}
