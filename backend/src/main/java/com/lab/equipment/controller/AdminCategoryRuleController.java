package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.entity.CategoryRule;
import com.lab.equipment.service.CategoryRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 设备分类预约时长规则（/api/admin/** 已由拦截器限定为管理员） */
@RestController
@RequestMapping("/api/admin/category-rules")
@RequiredArgsConstructor
public class AdminCategoryRuleController {

    private final CategoryRuleService categoryRuleService;

    @GetMapping
    public Result<List<CategoryRule>> list() {
        return Result.ok(categoryRuleService.list());
    }

    @PutMapping
    public Result<Void> update(@RequestBody List<CategoryRule> rules) {
        categoryRuleService.updateBatch(rules);
        return Result.ok();
    }
}
