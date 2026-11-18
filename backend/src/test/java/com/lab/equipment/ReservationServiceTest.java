package com.lab.equipment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;

/**
 * 实验室设备预约系统 JUnit5 单元测试
 * 测试预约时间合法性校验逻辑
 */
public class ReservationServiceTest {

    /**
     * 用例1：A=true，B=true；开始时间在过去，结束大于开始
     */
    @Test
    void testCase_ATrue_BTrue() {
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        LocalDateTime end = start.plusHours(1);
        boolean result = checkTime(start, end);
        assertFalse(result, "起始时间为过去，预约非法");
    }

    /**
     * 用例2：A=true，B=false；开始时间在过去，结束小于开始
     */
    @Test
    void testCase_ATrue_BFalse() {
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        LocalDateTime end = start.minusHours(1);
        boolean result = checkTime(start, end);
        assertFalse(result, "起始时间为过去，预约非法");
    }

    /**
     * 用例3：A=false，B=true；开始时间在将来，结束大于开始（合法预约）
     */
    @Test
    void testCase_AFalse_BTrue() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(2);
        boolean result = checkTime(start, end);
        assertTrue(result, "合法预约时间应该返回true");
    }

    /**
     * 用例4：A=false，B=false；开始时间在将来，结束小于开始
     */
    @Test
    void testCase_AFalse_BFalse() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.minusHours(1);
        boolean result = checkTime(start, end);
        assertFalse(result, "结束早于开始，预约非法");
    }

    /**
     * 被测试的业务方法：预约时间校验
     * @param startTime 预约开始
     * @param endTime 预约结束
     * @return true=时间合法
     */
    public boolean checkTime(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        // A：startTime是否在过去
        boolean A = startTime.isBefore(now);
        // B：endTime是否晚于startTime
        boolean B = endTime.isAfter(startTime);
        // 业务语义：开始不在过去 并且 结束晚于开始，才返回true
        return !A && B;
    }
}
