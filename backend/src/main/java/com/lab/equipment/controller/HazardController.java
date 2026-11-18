package com.lab.equipment.controller;

import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.HazardReport;
import com.lab.equipment.mapper.HazardReportMapper;
import com.lab.equipment.service.MessageService;
import com.lab.equipment.service.OpLogService;
import com.lab.equipment.entity.Message;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 安全隐患上报（所有登录用户） */
@RestController
@RequestMapping("/api/hazards")
@RequiredArgsConstructor
public class HazardController {

    private final HazardReportMapper hazardReportMapper;
    private final MessageService messageService;
    private final OpLogService opLogService;
    private final UserMapper userMapper;

    /** 提交隐患上报：通知所有安全员与管理员 */
    @PostMapping
    public Result<Void> report(@RequestBody ReportDTO dto) {
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException("请描述安全隐患");
        }
        User reporter = UserContext.get();
        HazardReport h = new HazardReport();
        h.setUserId(reporter.getId());
        h.setEquipmentId(dto.getEquipmentId());
        h.setContent(dto.getContent().trim());
        h.setStatus(HazardReport.REPORTED);
        hazardReportMapper.insert(h);

        // 通知安全员与管理员
        List<User> receivers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getRole, "SAFETY", "ADMIN"));
        for (User receiver : receivers) {
            messageService.push(receiver.getId(), "收到安全隐患上报",
                    "用户 " + (reporter.getRealName() != null ? reporter.getRealName() : reporter.getUsername())
                            + " 上报了安全隐患：" + dto.getContent().trim(),
                    Message.TYPE_SYSTEM);
        }
        opLogService.log("REPORT_HAZARD", "上报安全隐患：" + dto.getContent().trim());
        return Result.ok();
    }

    @Data
    public static class ReportDTO {
        private Long equipmentId;
        @NotBlank(message = "请描述安全隐患")
        private String content;
    }
}
