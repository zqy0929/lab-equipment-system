package com.lab.equipment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 教师批量为本班学生提交预约 */
@Data
public class ReservationBatchDTO {
    @NotNull(message = "请选择设备")
    private Long equipmentId;
    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;
    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;
    @NotNull(message = "请填写用途说明")
    private String purpose;
    /** 学生用户名列表（教师为本班学生代提交） */
    @NotEmpty(message = "请填写学生账号")
    private List<String> usernames;
}
