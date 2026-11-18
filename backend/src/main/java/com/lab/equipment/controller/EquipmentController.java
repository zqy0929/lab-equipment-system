package com.lab.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.Result;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 设备名录：登录用户可查，管理员增删改（见 AdminEquipmentController） */
@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @GetMapping("/page")
    public Result<Page<Equipment>> page(@RequestParam(defaultValue = "") String keyword,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String tag,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        // tag 支持逗号分隔的多标签（设备大类 + 使用属性可叠加），如：光学仪器,可外借
        List<String> tags = tag == null ? List.of()
                : java.util.Arrays.stream(tag.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return Result.ok(equipmentService.page(keyword, category, status, tags, page, size));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.ok(equipmentService.categories());
    }

    /** 可预约设备下拉列表 */
    @GetMapping("/available")
    public Result<List<Equipment>> available() {
        return Result.ok(equipmentService.listAvailable());
    }

    @GetMapping("/{id}")
    public Result<Equipment> detail(@PathVariable Long id) {
        return Result.ok(equipmentService.detail(id));
    }
}
