package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.entity.Notice;
import com.lab.equipment.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 公告管理（仅管理员，路径由拦截器校验 ADMIN 角色） */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    @PostMapping
    public Result<Void> add(@RequestBody Notice notice) {
        noticeService.add(notice);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.ok();
    }
}
