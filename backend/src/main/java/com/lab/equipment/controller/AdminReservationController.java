package com.lab.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.dto.ApprovalDTO;
import com.lab.equipment.entity.Reservation;
import com.lab.equipment.entity.User;
import com.lab.equipment.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 借用审批（管理员与设备保管员），含取消限制管理（取消记录 / 封禁名单 / 解禁） */
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final ReservationService reservationService;
    private final com.lab.equipment.service.CancelBanService cancelBanService;

    @GetMapping("/page")
    public Result<Page<Reservation>> page(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        return Result.ok(reservationService.adminPage(status, page, size));
    }

    @PutMapping("/approve")
    public Result<Void> approve(@Valid @RequestBody ApprovalDTO dto) {
        User admin = UserContext.get();
        reservationService.approve(new ReservationService.ApprovalContext(
                dto.getId(), dto.getApproved(), dto.getReason(), admin));
        return Result.ok();
    }

    /* ---------------- 取消限制管理 ---------------- */

    /** 全体用户取消记录分页（30 天取消统计口径） */
    @GetMapping("/cancel-records")
    public Result<Page<com.lab.equipment.entity.CancelRecord>> cancelRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(cancelBanService.recordPage(keyword, page, size));
    }

    /** 当前处于预约封禁中的用户名单（含剩余解禁天数） */
    @GetMapping("/cancel-bans")
    public Result<java.util.List<java.util.Map<String, Object>>> cancelBans() {
        return Result.ok(cancelBanService.bannedUsers());
    }

    /** 手动解除用户预约封禁（管理员 / 设备保管员豁免权限） */
    @PutMapping("/cancel-ban/{userId}/lift")
    public Result<Void> liftBan(@PathVariable Long userId) {
        cancelBanService.liftBan(userId, UserContext.get());
        return Result.ok();
    }
}
