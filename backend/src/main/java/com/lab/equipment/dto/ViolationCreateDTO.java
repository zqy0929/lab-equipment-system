package com.lab.equipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理员登记违约记录入参 */
@Data
public class ViolationCreateDTO {

    @NotNull(message = "请选择违约用户")
    private Long userId;

    /** 关联预约（可空） */
    private Long reservationId;

    /** NO_SHOW 爽约 / OVERDUE 超时归还 */
    @NotBlank(message = "请选择违约类型")
    private String type;

    @NotNull(message = "请填写扣减分数")
    @Min(value = 1, message = "扣减分数至少为 1")
    private Integer deduction;

    private String remark;
}
