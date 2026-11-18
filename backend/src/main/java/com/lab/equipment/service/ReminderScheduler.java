package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.equipment.entity.Message;
import com.lab.equipment.entity.Reservation;
import com.lab.equipment.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务：预约即将开始前提醒（每 5 分钟扫描一次，提前 30 分钟内且未提醒过的已批准预约）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReservationMapper reservationMapper;
    private final MessageService messageService;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void remindUpcoming() {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.eq("r.status", Reservation.APPROVED)
                .eq("r.remind_sent", 0)
                .gt("r.start_time", now)
                .le("r.start_time", now.plusMinutes(30));
        List<Reservation> upcoming = reservationMapper.selectWithDetailList(wrapper);
        for (Reservation r : upcoming) {
            messageService.push(r.getUserId(), "预约即将开始",
                    "您预约的「" + safeName(r) + "」将于 " + r.getStartTime().toLocalTime()
                            + " 开始（" + r.getStartTime().toLocalDate() + "），请准时到达实验室。",
                    Message.TYPE_RESERVATION);
            r.setRemindSent(1);
            reservationMapper.updateById(r);
        }
        if (!upcoming.isEmpty()) {
            log.info("已发送 {} 条预约即将开始提醒", upcoming.size());
        }
    }

    private String safeName(Reservation r) {
        return r.getEquipmentName() != null ? r.getEquipmentName() : ("设备 #" + r.getEquipmentId());
    }
}
