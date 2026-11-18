package com.lab.equipment.service;

import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.SysConfig;
import com.lab.equipment.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 系统配置：预约规则 / 信用阈值，带默认值兜底 */
@Service
@RequiredArgsConstructor
public class SysConfigService {

    public static final String KEY_ADVANCE_DAYS = "advance_days";
    public static final String KEY_MIN_DURATION = "min_duration_minutes";
    public static final String KEY_MAX_DURATION = "max_duration_minutes";
    public static final String KEY_CANCEL_CUTOFF = "cancel_cutoff_minutes";
    public static final String KEY_CREDIT_THRESHOLD = "credit_threshold";
    public static final String KEY_MAX_ACTIVE = "max_active_reservations";
    public static final String KEY_WEEKLY_HIGHRISK = "weekly_highrisk_limit";
    public static final String KEY_CLAIM_TIMEOUT = "claim_timeout_minutes";
    public static final String KEY_WAITLIST_EXPIRE_HOURS = "waitlist_expire_hours";
    public static final String KEY_EXPIRE_DEDUCTION = "expire_deduction";
    /** 30 天取消次数阈值：超过该次数限制提交新预约 */
    public static final String KEY_CANCEL_LIMIT_COUNT = "cancel_limit_count";
    /** 取消超限后的封禁天数 */
    public static final String KEY_CANCEL_BAN_DAYS = "cancel_ban_days";

    /** 默认值：配置表中缺失时兜底 */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry(KEY_ADVANCE_DAYS, "7"),
            Map.entry(KEY_MIN_DURATION, "30"),
            Map.entry(KEY_MAX_DURATION, "240"),
            Map.entry(KEY_CANCEL_CUTOFF, "120"),
            Map.entry(KEY_CREDIT_THRESHOLD, "60"),
            Map.entry(KEY_MAX_ACTIVE, "2"),
            Map.entry(KEY_WEEKLY_HIGHRISK, "3"),
            Map.entry(KEY_CLAIM_TIMEOUT, "30"),
            Map.entry(KEY_WAITLIST_EXPIRE_HOURS, "2"),
            Map.entry(KEY_EXPIRE_DEDUCTION, "5"),
            Map.entry(KEY_CANCEL_LIMIT_COUNT, "5"),
            Map.entry(KEY_CANCEL_BAN_DAYS, "7"));

    private final SysConfigMapper sysConfigMapper;
    private final OpLogService opLogService;

    /** 全部配置（key -> value），缺失项用默认值补齐 */
    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>(DEFAULTS);
        for (SysConfig c : sysConfigMapper.selectList(null)) {
            if (StringUtils.hasText(c.getConfigValue())) {
                map.put(c.getConfigKey(), c.getConfigValue());
            }
        }
        return map;
    }

    /** 读取整型配置 */
    public int getInt(String key) {
        try {
            return Integer.parseInt(asMap().get(key));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }

    /** 配置列表（管理页展示） */
    public List<SysConfig> listAll() {
        return sysConfigMapper.selectList(null);
    }

    /** 批量保存（存在则更新，不存在则插入） */
    @Transactional
    public void updateBatch(Map<String, String> values) {
        StringBuilder detail = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!DEFAULTS.containsKey(entry.getKey())) continue; // 只允许已知配置项
            String value = normalize(entry.getKey(), entry.getValue());
            SysConfig exist = sysConfigMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, entry.getKey()));
            if (exist != null) {
                if (!value.equals(exist.getConfigValue())) {
                    detail.append(entry.getKey()).append(" ").append(exist.getConfigValue())
                            .append(" → ").append(value).append("；");
                }
                exist.setConfigValue(value);
                sysConfigMapper.updateById(exist);
            } else {
                detail.append(entry.getKey()).append(" 新增 ").append(value).append("；");
                SysConfig c = new SysConfig();
                c.setConfigKey(entry.getKey());
                c.setConfigValue(value);
                c.setRemark("");
                sysConfigMapper.insert(c);
            }
        }
        if (detail.length() > 0) {
            opLogService.log("UPDATE_CONFIG", "修改系统预约规则：" + detail);
        }
    }

    /** 配置值校验与归一化（单人同时预约数量限制为 1-20 的整数） */
    private String normalize(String key, String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("配置项「" + key + "」的值不能为空");
        }
        if (KEY_MAX_ACTIVE.equals(key)) {
            int value;
            try {
                value = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("单人最大同时预约数量必须为整数");
            }
            if (value < 1 || value > 20) {
                throw new BusinessException("单人最大同时预约数量需在 1 - 20 台之间");
            }
            return String.valueOf(value);
        }
        if (KEY_CANCEL_LIMIT_COUNT.equals(key)) {
            int value;
            try {
                value = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("30 天取消次数阈值必须为整数");
            }
            if (value < 1 || value > 50) {
                throw new BusinessException("30 天取消次数阈值需在 1 - 50 次之间");
            }
            return String.valueOf(value);
        }
        if (KEY_CANCEL_BAN_DAYS.equals(key)) {
            int value;
            try {
                value = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("取消超限封禁天数必须为整数");
            }
            if (value < 1 || value > 365) {
                throw new BusinessException("取消超限封禁天数需在 1 - 365 天之间");
            }
            return String.valueOf(value);
        }
        return raw.trim();
    }
}
