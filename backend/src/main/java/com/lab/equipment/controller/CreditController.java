package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.User;
import com.lab.equipment.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 我的信用：个人资料页查看信用分与违约历史 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    /** 我的信用分 */
    @GetMapping("/my")
    public Result<Integer> myScore() {
        User user = UserContext.get();
        return Result.ok(user.getCreditScore() == null ? 100 : user.getCreditScore());
    }

    /** 我的违约记录 */
    @GetMapping("/my/violations")
    public Result<Object> myViolations() {
        return Result.ok(creditService.myViolations(UserContext.getUserId()));
    }
}
