package com.lab.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.Message;
import com.lab.equipment.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 站内消息中心：我的消息 / 未读数 / 标记已读 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public Result<Page<Message>> my(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size) {
        return Result.ok(messageService.myPage(UserContext.getUserId(), page, size));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(messageService.unreadCount(UserContext.getUserId()));
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        messageService.markRead(id, UserContext.getUserId());
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<Void> readAll() {
        messageService.markAllRead(UserContext.getUserId());
        return Result.ok();
    }
}
