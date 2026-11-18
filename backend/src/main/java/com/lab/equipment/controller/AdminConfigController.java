package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 系统配置（/api/admin/** 已由拦截器限定为管理员） */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {

    private final SysConfigService sysConfigService;

    @GetMapping
    public Result<Map<String, String>> all() {
        return Result.ok(sysConfigService.asMap());
    }

    @PutMapping
    public Result<Void> update(@RequestBody Map<String, String> values) {
        sysConfigService.updateBatch(values);
        return Result.ok();
    }
}
