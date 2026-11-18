package com.lab.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.Result;
import com.lab.equipment.entity.OpLog;
import com.lab.equipment.mapper.OpLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** 操作审计日志（仅管理员查看，禁止修改/删除） */
@RestController
@RequestMapping("/api/admin/oplogs")
@RequiredArgsConstructor
public class OpLogController {

    private final OpLogMapper opLogMapper;

    @GetMapping("/page")
    public Result<Page<OpLog>> page(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String action,
                                    @RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "15") long size) {
        QueryWrapper<OpLog> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("username", keyword).or().like("detail", keyword));
        }
        if (StringUtils.hasText(action)) wrapper.eq("action", action);
        wrapper.orderByDesc("create_time");
        return Result.ok(opLogMapper.selectPage(new Page<>(page, size), wrapper));
    }
}
