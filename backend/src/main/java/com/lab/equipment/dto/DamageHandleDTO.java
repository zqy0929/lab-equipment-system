package com.lab.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DamageHandleDTO {
    @NotNull(message = "缺少记录 ID")
    private Long id;
    @NotBlank(message = "缺少处理结果状态")
    private String status;       // FIXING / FIXED
    private BigDecimal cost;
    private String handleNote;
}
