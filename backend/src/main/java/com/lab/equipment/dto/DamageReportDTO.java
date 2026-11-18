package com.lab.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DamageReportDTO {
    @NotNull(message = "请选择设备")
    private Long equipmentId;
    @NotBlank(message = "请描述损耗情况")
    private String description;
    private String severity;     // MINOR / MODERATE / SEVERE，默认 MINOR
}
