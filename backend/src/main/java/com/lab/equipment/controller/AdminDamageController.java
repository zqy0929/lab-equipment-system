package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.dto.DamageHandleDTO;
import com.lab.equipment.service.DamageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 损耗处理（仅管理员，路径由拦截器校验 ADMIN 角色） */
@RestController
@RequestMapping("/api/admin/damages")
@RequiredArgsConstructor
public class AdminDamageController {

    private final DamageService damageService;

    @PutMapping("/handle")
    public Result<Void> handle(@Valid @RequestBody DamageHandleDTO dto) {
        damageService.handle(dto);
        return Result.ok();
    }
}
