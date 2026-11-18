package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 设备管理（设备维护：管理员与设备保管员，路径由拦截器校验） */
@RestController
@RequestMapping("/api/admin/equipment")
@RequiredArgsConstructor
public class AdminEquipmentController {

    private final EquipmentService equipmentService;

    @PostMapping
    public Result<Void> add(@RequestBody Equipment equipment) {
        equipmentService.add(equipment);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Equipment equipment) {
        equipmentService.update(equipment);
        return Result.ok();
    }

    /** 切换设备运行状态：正常 / 维修中 / 停用 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        equipmentService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return Result.ok();
    }
}
