package com.lab.equipment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalDTO {
    @NotNull(message = "缺少预约记录 ID")
    private Long id;
    @NotNull(message = "缺少审批结果")
    private Boolean approved;    // true 通过 / false 驳回
    private String reason;       // 驳回原因（驳回时必填由 Service 校验）
}
