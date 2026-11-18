package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.Message;
import com.lab.equipment.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 站内消息：推送 / 未读数 / 标记已读 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    /** 推送一条站内消息 */
    public void push(Long userId, String title, String content, String type) {
        Message m = new Message();
        m.setUserId(userId);
        m.setTitle(title);
        m.setContent(content == null ? "" : content);
        m.setType(type == null ? Message.TYPE_SYSTEM : type);
        m.setIsRead(0);
        messageMapper.insert(m);
    }

    /** 我的消息（分页，最新在前） */
    public Page<Message> myPage(Long userId, long page, long size) {
        return messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .orderByDesc(Message::getCreateTime));
    }

    /** 未读消息数 */
    public long unreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0));
    }

    /** 标记单条已读（仅本人的消息） */
    public void markRead(Long id, Long userId) {
        Message m = messageMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException("消息不存在");
        }
        if (m.getIsRead() == null || m.getIsRead() == 0) {
            m.setIsRead(1);
            messageMapper.updateById(m);
        }
    }

    /** 全部已读 */
    public void markAllRead(Long userId) {
        Message template = new Message();
        template.setIsRead(1);
        messageMapper.update(template, new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0));
    }
}
