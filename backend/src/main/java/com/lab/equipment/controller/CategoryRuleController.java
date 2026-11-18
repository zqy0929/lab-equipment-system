package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.entity.CategoryRule;
import com.lab.equipment.service.CategoryRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 分类时长规则（只读，供预约页面展示时长限制提示，所有登录用户可访问） */
@RestController
@RequestMapping("/api/category-rules")
@RequiredArgsConstructor
public class CategoryRuleController {

    private final CategoryRuleService categoryRuleService;

    @GetMapping
    public Result<List<CategoryRule>> list() {
        return Result.ok(categoryRuleService.list());
    }
}
