package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.entity.Notice;
import com.lab.equipment.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 通知公告：所有登录用户可查看 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public Result<List<Notice>> list() {
        return Result.ok(noticeService.list());
    }
}
