package com.lab.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.Result;
import com.lab.equipment.dto.DamageHandleDTO;
import com.lab.equipment.dto.DamageReportDTO;
import com.lab.equipment.entity.DamageRecord;
import com.lab.equipment.service.DamageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 损耗记录：所有人可上报/查看，管理员处理 */
@RestController
@RequestMapping("/api/damages")
@RequiredArgsConstructor
public class DamageController {

    private final DamageService damageService;

    @PostMapping
    public Result<Void> report(@Valid @RequestBody DamageReportDTO dto) {
        damageService.report(dto);
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<Page<DamageRecord>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(damageService.page(status, page, size));
    }
}
