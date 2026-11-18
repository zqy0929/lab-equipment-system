package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.Notice;
import com.lab.equipment.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;

    public List<Notice> list() {
        return noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .orderByDesc(Notice::getCreateTime).last("LIMIT 50"));
    }

    public void add(Notice notice) {
        var user = UserContext.get();
        notice.setPublisherName(user != null && user.getRealName() != null ? user.getRealName() : "管理员");
        noticeMapper.insert(notice);
    }

    public void delete(Long id) {
        if (noticeMapper.selectById(id) == null) throw new BusinessException("公告不存在");
        noticeMapper.deleteById(id);
    }
}
