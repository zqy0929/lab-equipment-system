package com.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 违约记录：登记后自动扣减信用分 */
@Data
@TableName("violation_record")
public class ViolationRecord {
    public static final String NO_SHOW = "NO_SHOW";     // 预约爽约
    public static final String OVERDUE = "OVERDUE";     // 超时归还

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long reservationId;
    private String type;
    private Integer deduction;
    private String remark;
    private Long operatorId;
    @TableField(exist = false)
    private String username;       // 关联查询展示
    @TableField(exist = false)
    private String realName;
    @TableField(exist = false)
    private String operatorName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
