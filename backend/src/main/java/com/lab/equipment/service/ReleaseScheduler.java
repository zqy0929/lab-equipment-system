package com.lab.equipment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 预约定时任务：1) 超时未领用自动释放；2) 超过结束时间自动标记【已过期】 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseScheduler {

    private final ReservationService reservationService;

    /** 每 2 分钟扫描一次，启动延迟 90 秒 */
    @Scheduled(fixedDelay = 2 * 60 * 1000, initialDelay = 90 * 1000)
    public void releaseExpired() {
        try {
            int released = reservationService.autoReleaseExpired();
            if (released > 0) {
                log.info("[自动释放] 本轮释放超时未领用预约 {} 条", released);
            }
        } catch (Exception e) {
            log.error("[自动释放] 执行失败", e);
        }
        try {
            int expired = reservationService.autoExpireReservations();
            if (expired > 0) {
                log.info("[预约过期] 本轮标记过期预约 {} 条", expired);
            }
        } catch (Exception e) {
            log.error("[预约过期] 执行失败", e);
        }
    }
}
