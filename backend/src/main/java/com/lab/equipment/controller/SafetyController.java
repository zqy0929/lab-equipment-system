package com.lab.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.HazardReport;
import com.lab.equipment.entity.Reservation;
import com.lab.equipment.entity.SafetyReadRecord;
import com.lab.equipment.mapper.HazardReportMapper;
import com.lab.equipment.mapper.ReservationMapper;
import com.lab.equipment.mapper.SafetyReadRecordMapper;
import com.lab.equipment.service.MessageService;
import com.lab.equipment.service.OpLogService;
import com.lab.equipment.entity.Message;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 安全中心（安全员 + 管理员）：高危设备使用记录 / 安全须知阅读记录 / 隐患上报处理 */
@RestController
@RequestMapping("/api/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final ReservationMapper reservationMapper;
    private final SafetyReadRecordMapper safetyReadRecordMapper;
    private final HazardReportMapper hazardReportMapper;
    private final MessageService messageService;
    private final OpLogService opLogService;

    /** 高危设备使用/预约记录（分页，含状态过滤） */
    @GetMapping("/high-risk")
    public Result<Page<Reservation>> highRisk(@RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.isNotNull("e.id")
                .apply("(e.tags LIKE '%高温设备%' OR e.tags LIKE '%危险化学品配套设备%')");
        if (StringUtils.hasText(status)) wrapper.eq("r.status", status);
        wrapper.orderByDesc("r.start_time");
        return Result.ok(reservationMapper.selectWithDetailPage(new Page<>(page, size), wrapper));
    }

    /** 安全须知阅读记录（分页） */
    @GetMapping("/read-records")
    public Result<Page<SafetyReadRecord>> readRecords(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size) {
        QueryWrapper<SafetyReadRecord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("s.create_time");
        return Result.ok(safetyReadRecordMapper.selectWithDetailPage(new Page<>(page, size), wrapper));
    }

    /** 隐患上报列表（分页） */
    @GetMapping("/hazards")
    public Result<Page<HazardReport>> hazards(@RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        QueryWrapper<HazardReport> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) wrapper.eq("h.status", status);
        wrapper.orderByAsc("h.status").orderByDesc("h.create_time");
        return Result.ok(hazardReportMapper.selectWithDetailPage(new Page<>(page, size), wrapper));
    }

    /** 处理隐患上报（仅管理员；安全员为只读核查视图，无处理权限） */
    @PutMapping("/hazards/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleDTO dto) {
        var operator = UserContext.get();
        if (operator == null || !"ADMIN".equals(operator.getRole())) {
            throw new BusinessException("无权限：隐患处理仅系统管理员可操作");
        }
        HazardReport h = hazardReportMapper.selectById(id);
        if (h == null) throw new BusinessException("隐患记录不存在");
        h.setStatus(HazardReport.HANDLED);
        h.setHandleNote(dto.getHandleNote());
        h.setHandleTime(LocalDateTime.now());
        h.setHandlerName(operator.getUsername());
        hazardReportMapper.updateById(h);
        // 通知上报人处理结果
        messageService.push(h.getUserId(), "隐患上报已处理",
                "您上报的安全隐患已由 " + h.getHandlerName() + " 处理。处理说明：" +
                        (StringUtils.hasText(h.getHandleNote()) ? h.getHandleNote() : "已核验处理"),
                Message.TYPE_SYSTEM);
        opLogService.log("HANDLE_HAZARD", "处理隐患上报 #" + id);
        return Result.ok();
    }

    @Data
    public static class HandleDTO {
        private String handleNote;
    }
}
