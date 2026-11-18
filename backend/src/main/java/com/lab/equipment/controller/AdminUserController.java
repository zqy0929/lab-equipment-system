package com.lab.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.Result;
import com.lab.equipment.entity.User;
import com.lab.equipment.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 用户管理（仅管理员） */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping("/page")
    public Result<Page<User>> page(@RequestParam(defaultValue = "") String keyword,
                                   @RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long size) {
        return Result.ok(userService.page(keyword, page, size));
    }

    @PutMapping("/{id}/role")
    public Result<Void> role(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.updateRole(id, body.get("role"));
        return Result.ok();
    }

    /** 注册审核：action = APPROVED（通过）/ REJECTED（驳回） */
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.audit(id, body.get("action"));
        return Result.ok();
    }

    @PutMapping("/{id}/blacklist")
    public Result<Void> blacklist(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.updateBlacklist(id, body.get("blacklisted"));
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> status(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    public Result<Void> password(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
