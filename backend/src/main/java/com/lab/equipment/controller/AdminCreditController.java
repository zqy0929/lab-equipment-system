package com.lab.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.dto.ViolationCreateDTO;
import com.lab.equipment.entity.User;
import com.lab.equipment.entity.ViolationRecord;
import com.lab.equipment.service.CreditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 信用积分与违约管理（/api/admin/** 已由拦截器限定为管理员） */
@RestController
@RequestMapping("/api/admin/credits")
@RequiredArgsConstructor
public class AdminCreditController {

    private final CreditService creditService;

    /** 用户信用列表（按信用分升序，最低分在前） */
    @GetMapping("/users")
    public Result<Page<User>> users(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size) {
        return Result.ok(creditService.creditPage(keyword, page, size));
    }

    /** 违约记录列表 */
    @GetMapping("/violations")
    public Result<Page<ViolationRecord>> violations(@RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return Result.ok(creditService.violationPage(userId, type, page, size));
    }

    /** 登记违约（自动扣减信用分并消息通知本人） */
    @PostMapping("/violations")
    public Result<Void> register(@Valid @RequestBody ViolationCreateDTO dto) {
        creditService.registerViolation(dto, UserContext.get());
        return Result.ok();
    }
}
