package com.lab.equipment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 修改预约时段（仅待审批状态） */
@Data
public class ReservationModifyDTO {
    @NotNull(message = "缺少预约记录 ID")
    private Long id;
    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;
    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;
}
