package com.lab.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.User;
import com.lab.equipment.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 个人信息接口（登录用户本人） */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<User> me() {
        return Result.ok(UserContext.get());
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody ProfileUpdateBody body) {
        Long id = UserContext.getUserId();
        userService.updateProfile(id, body.getRealName(), body.getGender(),
                body.getDepartment(), body.getPhone(), body.getEmail());
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> password(@RequestBody Map<String, String> body) {
        Long id = UserContext.getUserId();
        userService.resetPassword(id, body.get("newPassword"));
        return Result.ok();
    }

    @Data
    public static class ProfileUpdateBody {
        private String realName;
        private String gender;
        private String department;
        private String phone;
        private String email;
    }
}
