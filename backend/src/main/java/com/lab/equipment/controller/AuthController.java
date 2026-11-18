package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.dto.LoginDTO;
import com.lab.equipment.dto.RegisterDTO;
import com.lab.equipment.entity.User;
import com.lab.equipment.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.lab.equipment.service.CaptchaService captchaService;

    /** 图形验证码（注册等匿名流程的人机校验）：返回 captchaId + base64 图片 */
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        return Result.ok(captchaService.generate());
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        // 验证码校验：不通过抛业务异常（错误信息统一为「验证码错误/已失效/已过期」）
        captchaService.verify(dto.getCaptchaId(), dto.getCaptchaCode());
        authService.register(dto);
        return Result.ok();
    }
}
