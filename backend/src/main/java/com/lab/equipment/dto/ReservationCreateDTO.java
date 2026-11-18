package com.lab.equipment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationCreateDTO {
    @NotNull(message = "请选择设备")
    private Long equipmentId;
    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;
    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;
    @NotNull(message = "请填写用途说明")
    private String purpose;
    /** 高危设备必须勾选《安全须知》后置为 true */
    private Boolean safetyAcknowledged;
}
